package com.loading.process.mcp;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/mcp")
@CrossOrigin(origins = "*", exposedHeaders = { "MCP-Session-Id", "MCP-Protocol-Version" })
@Slf4j
public class McpHttpServer {

    private static final String DEFAULT_PROTOCOL_VERSION = "2026-07-28";
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of("2025-11-25", "2026-07-28");
    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;
    private static final int REQUEST_CANCELLED = -32800;
    private static final long SSE_HEARTBEAT_SECONDS = 15;

    private final ObjectMapper objectMapper;
    private final McpController tools;
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();
    private volatile String latestSessionId;
    private final ExecutorService requestExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService eventExecutor = Executors.newCachedThreadPool();

    public McpHttpServer(ObjectMapper objectMapper, McpController tools) {
        this.objectMapper = objectMapper;
        this.tools = tools;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = { MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_EVENT_STREAM_VALUE })
    public ResponseEntity<String> handle(@RequestBody String body,
            @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId) {
        McpSession session = getOrCreateSession(sessionId);
        long startedAt = System.nanoTime();
        log.info("MCP request started: method=POST path=/mcp sessionId={}", session.id());
        try {
            JsonNode request = objectMapper.readTree(body);
            if (request == null || !request.isObject()) {
                return jsonResponse(error(null, INVALID_REQUEST, "Request must be a JSON object"), session);
            }
            if (request.path("jsonrpc").asString().isBlank()
                    || !"2.0".equals(request.path("jsonrpc").asString())) {
                return jsonResponse(error(request.get("id"), INVALID_REQUEST, "jsonrpc must be 2.0"), session);
            }

            String method = request.path("method").asString(null);
            if (method == null || method.isBlank()) {
                return jsonResponse(error(request.get("id"), INVALID_REQUEST, "method is required"), session);
            }
            log.info("MCP JSON-RPC method={} id={} sessionId={} protocolVersion={}",
                    method, request.get("id"), session.id(),
                    request.path("params").path("protocolVersion").asString(""));
            JsonNode id = request.get("id");
            JsonNode params = request.path("params");

            if (method.startsWith("notifications/")) {
                handleNotification(method, params, session);
                return noContent(session);
            }

            if (id == null || id.isNull()) {
                return jsonResponse(error(null, INVALID_REQUEST, "Requests must have an id"), session);
            }

            Future<ObjectNode> task = requestExecutor.submit(() -> dispatch(method, id, params, session));
            session.activeRequests().put(id.asString(), task);
            try {
                return jsonResponse(task.get(), session);
            } catch (CancellationException exception) {
                return jsonResponse(error(id, REQUEST_CANCELLED, "Request was cancelled"), session);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return jsonResponse(error(id, REQUEST_CANCELLED, "Request was interrupted"), session);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof McpException mcpException) {
                    return jsonResponse(error(id, mcpException.code(), mcpException.getMessage()), session);
                }
                if (cause instanceof ResponseStatusException responseStatusException) {
                    return jsonResponse(error(id, INVALID_PARAMS, responseStatusException.getReason()), session);
                }
                return jsonResponse(error(id, INTERNAL_ERROR, cause == null ? "Internal error" : cause.getMessage()),
                        session);
            } finally {
                session.activeRequests().remove(id.asString());
            }
        } catch (Exception exception) {
            return jsonResponse(error(null, PARSE_ERROR, "Invalid JSON: " + exception.getMessage()), session);
        } finally {
            log.info("MCP request completed: method=POST path=/mcp sessionId={} durationMs={}",
                    session.id(), (System.nanoTime() - startedAt) / 1_000_000);
        }
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestHeader(value = "MCP-Session-Id", required = false) String sessionId) {
        McpSession session = getOrCreateSession(sessionId);
        log.info("MCP event stream started: method=GET path=/mcp sessionId={}", session.id());
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        emitter.onCompletion(() -> log.info("MCP event stream completed: sessionId={}", session.id()));
        emitter.onTimeout(() -> log.info("MCP event stream timed out: sessionId={}", session.id()));
        eventExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().comment("connected"));
                while (true) {
                    String event = session.events().poll(SSE_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
                    if (event == null) {
                        emitter.send(SseEmitter.event().comment("keep-alive"));
                        continue;
                    }
                    emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
                }
            } catch (Exception exception) {
                if (isClientDisconnect(exception)) {
                    log.debug("MCP event stream closed by client: sessionId={} message={}",
                            session.id(), exception.getMessage());
                    emitter.complete();
                } else {
                    log.warn("MCP event stream failed: sessionId={} message={}", session.id(), exception.getMessage());
                    emitter.completeWithError(exception);
                }
            }
        });
        return emitter;
    }

    private boolean isClientDisconnect(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof IOException
                    || current.getMessage() != null && (current.getMessage().contains("Connection reset")
                            || current.getMessage().contains("Broken pipe")
                            || current.getMessage().contains("ClientAbortException"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ObjectNode dispatch(String method, JsonNode id, JsonNode params, McpSession session) {
        return switch (method) {
            case "initialize" -> result(id, initializeResult(params, session));
            case "capabilities" -> result(id, capabilities());
            case "notifications/initialized" -> result(id, objectMapper.createObjectNode());
            case "tools/list" -> result(id, toolsList());
            case "tools/call" -> result(id, callTool(params, session));
            case "ping" -> result(id, objectMapper.createObjectNode());
            default -> error(id, METHOD_NOT_FOUND, "Unknown method: " + method);
        };
    }

    private ObjectNode toolsList() {
        ArrayNode listedTools = objectMapper.createArrayNode();
        ObjectNode listSchema = objectMapper.createObjectNode().put("type", "object");
        listSchema.putObject("properties").putObject("type").put("type", "string");
        listSchema.withObject("properties").putObject("status").put("type", "string");
        listedTools.add(tool("list_objects", "List objects, optionally filtered by type and status.", listSchema));
        listedTools.add(tool("get_object", "Retrieve an object by id.", schema("id")));
        listedTools.add(tool("create_object", "Create a business object.",
                schema("name", "type", "status", "currentValue", "nextServiceDate")));
        listedTools.add(tool("delete_object", "Delete an object by id.", schema("id")));
        return objectMapper.createObjectNode().set("tools", listedTools);
    }

    private ObjectNode capabilities() {
        ObjectNode capabilities = objectMapper.createObjectNode();
        capabilities.putObject("tools").put("listChanged", false);
        capabilities.putObject("logging");
        return capabilities;
    }

    private ObjectNode initializeResult(JsonNode params, McpSession session) {
        ObjectNode result = objectMapper.createObjectNode();
        String requestedVersion = params.path("protocolVersion").asString(null);
        String negotiatedVersion = SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)
                ? requestedVersion
                : DEFAULT_PROTOCOL_VERSION;
        session.setProtocolVersion(negotiatedVersion);
        result.put("protocolVersion", negotiatedVersion);
        result.set("capabilities", capabilities());
        result.putObject("serverInfo")
                .put("name", "loading-process")
                .put("version", "1.0.0");
        return result;
    }

    private ObjectNode callTool(JsonNode params, McpSession session) {
        String name = params.path("name").asString(null);
        JsonNode arguments = params.path("arguments");
        if (name == null || name.isBlank()) {
            throw new McpException(INVALID_PARAMS, "tools/call requires params.name");
        }
        session.publish(progressNotification(name, 0, 1, "started"));
        try {
            Object value = switch (name) {
                case "list_objects" -> tools.listObjects(text(arguments, "type"), text(arguments, "status"));
                case "get_object" -> tools.getObject(requiredText(arguments, "id"));
                case "create_object" -> tools.createObject(
                        requiredText(arguments, "name"), requiredText(arguments, "type"),
                        text(arguments, "status"), decimal(arguments, "currentValue"),
                        date(arguments, "nextServiceDate"));
                case "delete_object" -> tools.deleteObject(requiredText(arguments, "id"));
                default -> throw new McpException(METHOD_NOT_FOUND, "Unknown tool: " + name);
            };
            session.publish(progressNotification(name, 1, 1, "completed"));
            ObjectNode content = objectMapper.createObjectNode();
            content.put("type", "text");
            content.put("text", objectMapper.writeValueAsString(value));
            return objectMapper.createObjectNode().set("content", objectMapper.createArrayNode().add(content))
                    .put("isError", false);
        } catch (McpException exception) {
            throw exception;
        } catch (ResponseStatusException exception) {
            return toolError(exception.getReason());
        } catch (Exception exception) {
            return toolError(exception.getMessage());
        }
    }

    private ObjectNode toolError(String message) {
        ObjectNode content = objectMapper.createObjectNode()
                .put("type", "text")
                .put("text", message == null || message.isBlank() ? "Tool execution failed" : message);
        return objectMapper.createObjectNode()
                .set("content", objectMapper.createArrayNode().add(content))
                .put("isError", true);
    }

    private void handleNotification(String method, JsonNode params, McpSession session) {
        if ("notifications/cancelled".equals(method)) {
            String requestId = params.path("requestId").asString(null);
            if (requestId != null) {
                session.cancel(requestId);
                session.publish(progressNotification(requestId, 0, 1, "cancelled"));
            }
        } else if ("notifications/progress".equals(method)) {
            session.publish(notification("notifications/progress", params));
        }
    }

    private McpSession getOrCreateSession(String requestedId) {
        if (requestedId != null && sessions.containsKey(requestedId)) {
            return sessions.get(requestedId);
        }
        if (requestedId == null && latestSessionId != null) {
            McpSession latestSession = sessions.get(latestSessionId);
            if (latestSession != null) {
                return latestSession;
            }
        }
        McpSession session = new McpSession(UUID.randomUUID().toString());
        sessions.put(session.id(), session);
        latestSessionId = session.id();
        return session;
    }

    private ResponseEntity<String> jsonResponse(ObjectNode body, McpSession session) {
        return ResponseEntity.ok()
                .header("MCP-Session-Id", session.id())
                .header("MCP-Protocol-Version", session.protocolVersion())
                .body(body.toString());
    }

    private ResponseEntity<String> noContent(McpSession session) {
        return ResponseEntity.accepted()
                .header("MCP-Session-Id", session.id())
                .header("MCP-Protocol-Version", session.protocolVersion())
                .build();
    }

    private ObjectNode result(JsonNode id, JsonNode result) {
        return objectMapper.createObjectNode().put("jsonrpc", "2.0").set("id", id).set("result", result);
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .set("id", id);
        response.putObject("error")
                .put("code", code)
                .put("message", message == null ? "error" : message);
        return response;
    }

    private ObjectNode notification(String method, JsonNode params) {
        return objectMapper.createObjectNode().put("jsonrpc", "2.0").put("method", method).set("params", params);
    }

    private ObjectNode progressNotification(String token, int progress, int total, String message) {
        return notification("notifications/progress", objectMapper.createObjectNode()
                .put("progressToken", token).put("progress", progress).put("total", total).put("message", message));
    }

    private ObjectNode tool(String name, String description, ObjectNode inputSchema) {
        return objectMapper.createObjectNode().put("name", name).put("description", description).set("inputSchema",
                inputSchema);
    }

    private ObjectNode schema(String... fields) {
        ObjectNode schema = objectMapper.createObjectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        for (String field : fields) {
            properties.putObject(field).put("type", field.equals("currentValue") ? "number" : "string");
        }
        return schema;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new McpException(INVALID_PARAMS, field + " is required");
        }
        return value;
    }

    private java.math.BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        return value == null || value.isBlank() ? null : new java.math.BigDecimal(value);
    }

    private java.time.LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        return value == null || value.isBlank() ? null : java.time.LocalDate.parse(value);
    }

    private static final class McpException extends RuntimeException {
        private final int code;

        private McpException(int code, String message) {
            super(message);
            this.code = code;
        }

        private int code() {
            return code;
        }
    }

    private static final class McpSession {
        private final String id;
        private volatile String protocolVersion = DEFAULT_PROTOCOL_VERSION;
        private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
        private final Map<String, Future<ObjectNode>> activeRequests = new ConcurrentHashMap<>();
        private final java.util.Set<String> cancelledRequests = ConcurrentHashMap.newKeySet();

        private McpSession(String id) {
            this.id = id;
        }

        private String id() {
            return id;
        }

        private String protocolVersion() {
            return protocolVersion;
        }

        private void setProtocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        private LinkedBlockingQueue<String> events() {
            return events;
        }

        private Map<String, Future<ObjectNode>> activeRequests() {
            return activeRequests;
        }

        private void publish(JsonNode event) {
            events.offer(event.toString());
        }

        private void cancel(String requestId) {
            cancelledRequests.add(requestId);
            Future<ObjectNode> request = activeRequests.remove(requestId);
            if (request != null) {
                request.cancel(true);
            }
        }
    }
}
