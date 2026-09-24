package com.loading.process.mcp;

import com.loading.process.model.CreateObjectRequest;
import com.loading.process.model.ObjectResponse;
import com.loading.process.model.ObjectType;
import com.loading.process.model.ObjectStatus;
import com.loading.process.service.ObjectBusinessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class McpController {

    private final ObjectBusinessService objectBusinessService;

    public McpController(ObjectBusinessService objectBusinessService) {
        this.objectBusinessService = objectBusinessService;
    }

    public List<ObjectResponse> listObjects(String type, String status) {
        log.info("Executing MCP tool list_objects with type={} status={}", type, status);
        ObjectType objectType = type == null || type.isBlank() ? null : ObjectType.fromValue(type);
        List<ObjectResponse> objects = objectBusinessService.getObjects(objectType, ObjectStatus.fromValue(status));
        log.info("Completed MCP tool list_objects with {} results", objects != null ? objects.size() : 0);
        log.info("Objects list: {}", objects);
        return objects;
    }

    public ObjectResponse getObject(String id) {
        log.info("Executing MCP tool get_object with id={}", id);
        ObjectResponse object = objectBusinessService.getObject(id);
        log.info("Completed MCP tool get_object with id={} result={}", id, object);
        return object;
    }

    public ObjectResponse createObject(
            String name,
            String type,
            String status,
            BigDecimal currentValue,
            LocalDate nextServiceDate) {
        log.info("Executing MCP tool create_object with name={} type={} status={} currentValue={} nextServiceDate={}",
                name, type, status, currentValue, nextServiceDate);
        ObjectResponse createdObject = objectBusinessService.createObject(
                new CreateObjectRequest(name, ObjectType.fromValue(type), ObjectStatus.fromValue(status), currentValue,
                        nextServiceDate));
        log.info("Completed MCP tool create_object with created object={}", createdObject);
        return createdObject;
    }

    public Map<String, Object> deleteObject(String id) {
        log.info("Executing MCP tool delete_object with id={}", id);
        objectBusinessService.deleteObject(id);
        Map<String, Object> response = Map.of("deleted", true, "id", id);
        log.info("Completed MCP tool delete_object with response={}", response);
        return response;
    }
}