package com.loading.process.service;

import com.loading.process.model.CreateObjectRequest;
import com.loading.process.model.ObjectResponse;
import com.loading.process.model.UpdateObjectRequest;
import com.loading.process.repository.ObjectRepository;
import com.loading.process.model.EventLogRecord;
import com.loading.process.model.IntervalRecord;
import com.loading.process.model.ServiceTaskRecord;
import com.loading.process.model.ObjectType;
import com.loading.process.model.ObjectStatus;
import com.loading.process.repository.EventLogRepository;
import com.loading.process.repository.IntervalRepository;
import com.loading.process.repository.ServiceTaskRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import com.loading.process.model.EventLogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class ObjectBusinessService {

    private final Map<String, ObjectResponse> objectsById = new ConcurrentHashMap<>();
    private final Map<String, String> nameIndex = new ConcurrentHashMap<>();
    private final ObjectRepository objectRepository;
    private final EventLogRepository eventLogRepository;
    private final IntervalRepository intervalRepository;
    private final ServiceTaskRepository serviceTaskRepository;

    public ObjectBusinessService() {
        this(null, null, null, null);
    }

    public ObjectBusinessService(ObjectRepository objectRepository) {
        this(objectRepository, null, null, null);
    }

    public ObjectBusinessService(ObjectRepository objectRepository, EventLogRepository eventLogRepository) {
        this(objectRepository, eventLogRepository, null, null);
    }

    public ObjectBusinessService(ObjectRepository objectRepository, EventLogRepository eventLogRepository,
            IntervalRepository intervalRepository) {
        this(objectRepository, eventLogRepository, intervalRepository, null);
    }

    @Autowired
    public ObjectBusinessService(ObjectRepository objectRepository, EventLogRepository eventLogRepository,
            IntervalRepository intervalRepository, ServiceTaskRepository serviceTaskRepository) {
        this.objectRepository = objectRepository;
        this.eventLogRepository = eventLogRepository;
        this.intervalRepository = intervalRepository;
        this.serviceTaskRepository = serviceTaskRepository;
    }

    public ObjectResponse createObject(CreateObjectRequest request) {
        validateRequestNotNull(request);

        String name = normalizeName(request.name());
        ObjectType type = normalizeType(request.type());
        ObjectStatus status = normalizeStatus(request.status());
        BigDecimal currentValue = normalizeValue(request.currentValue());
        LocalDate nextServiceDate = normalizeNextServiceDate(request.nextServiceDate(), type);

        if (nameIndex.containsKey(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object with this name already exists");
        }

        String id = generateUuidV7();
        Instant now = Instant.now();
        ObjectResponse response = new ObjectResponse(
                id,
                name,
                type.value(),
                status,
                now,
                now,
                now,
                currentValue,
                nextServiceDate,
                java.util.List.of(), java.util.List.of(), java.util.List.of());

        objectsById.put(id, response);
        nameIndex.put(name, id);

        if (objectRepository != null) {
            objectRepository.save(response);
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getObjects(ObjectType type, ObjectStatus status) {
        log.info("Fetching objects with type='{}', status='{}'", type, status);
        ObjectStatus normalizedStatus = status;

        if (objectRepository != null) {
            List<ObjectResponse> result = objectRepository.findObjects(type, normalizedStatus);
            log.info("Loaded {} objects from SQLite repository", result.size());
            // Enrich event-type objects with their event logs
            List<ObjectResponse> enriched = result.stream().map(this::enrichWithEvents).toList();
            return (List<T>) enriched;
        }

        List<ObjectResponse> result = objectsById.values().stream()
                .filter(object -> type == null || object.type().equals(type.value()))
                .filter(object -> normalizedStatus == null || object.status() == normalizedStatus)
                .sorted(Comparator.comparing(ObjectResponse::createdAt).reversed())
                .toList();

        List<ObjectResponse> enriched = result.stream().map(this::enrichWithEvents).toList();

        log.info("Loaded {} objects from in-memory store", result.size());
        return (List<T>) enriched;
    }

    public ObjectResponse getObject(String id) {
        if (objectRepository != null) {
            ObjectResponse object = objectRepository.findById(id);
            if (object == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
            }
            return enrichWithEvents(object);
        }

        ObjectResponse object = objectsById.get(id);
        if (object == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
        }
        return enrichWithEvents(object);
    }

    public ObjectResponse updateObject(String id, UpdateObjectRequest request) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        validateRequestNotNull(request);

        ObjectResponse existing = objectsById.get(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
        }

        String name = normalizeName(request.name() == null ? existing.name() : request.name());
        ObjectType type = normalizeType(
                request.type() == null ? ObjectType.fromValue(existing.type()) : request.type());
        ObjectStatus status = normalizeStatus(request.status() == null ? existing.status() : request.status());
        BigDecimal currentValue = normalizeValue(
                request.currentValue() == null ? existing.currentValue() : request.currentValue());
        LocalDate nextServiceDate = normalizeNextServiceDate(
                request.nextServiceDate() == null ? existing.nextServiceDate() : request.nextServiceDate(), type);

        if (!existing.name().equals(name) && nameIndex.containsKey(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object with this name already exists");
        }

        nameIndex.remove(existing.name());
        nameIndex.put(name, id);

        Instant now = Instant.now();
        ObjectResponse updated = new ObjectResponse(
                id,
                name,
                type.value(),
                status,
                existing.createdAt(),
                now,
                now,
                currentValue,
                nextServiceDate,
                java.util.List.of(), java.util.List.of(), java.util.List.of());

        objectsById.put(id, updated);

        if (objectRepository != null) {
            objectRepository.update(updated);
        }

        return updated;
    }

    public void deleteObject(String id) {
        ObjectResponse object = objectsById.remove(id);
        if (object == null && objectRepository != null) {
            object = objectRepository.findById(id);
        }
        if (object == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
        }
        nameIndex.remove(object.name());

        if (objectRepository != null) {
            objectRepository.delete(id);
        }
    }

    private void validateRequestNotNull(Object request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        return name.trim();
    }

    private ObjectType normalizeType(ObjectType type) {
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }
        return type;
    }

    private ObjectStatus normalizeStatus(ObjectStatus status) {
        return status == null ? ObjectStatus.ACTIVE : status;
    }

    private BigDecimal normalizeValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private LocalDate normalizeNextServiceDate(LocalDate nextServiceDate, ObjectType type) {
        if (nextServiceDate == null
                && (ObjectType.CALENDAR == type || ObjectType.MILEAGE == type || ObjectType.COMBINED == type)) {
            return LocalDate.now().plusDays(30);
        }
        return nextServiceDate;
    }

    private String generateUuidV7() {
        long unixTimeMillis = Instant.now().toEpochMilli();
        long randomA = ThreadLocalRandom.current().nextLong(0x1000L);
        long randomB = ThreadLocalRandom.current().nextLong(0x3FFFFFFFFFFFFFFFL);

        long mostSigBits = (unixTimeMillis << 16) | ((7L & 0xF) << 12) | (randomA & 0xFFF);
        long leastSigBits = ((randomB & 0x3FFFFFFFFFFFFFFFL) << 1) | 0x8000000000000000L;

        return new UUID(mostSigBits, leastSigBits).toString();
    }

    private ObjectResponse enrichWithEvents(ObjectResponse obj) {
        if (obj == null)
            return null;
        // if no repositories available, ensure events/intervals/serviceTasks are
        // non-null
        if (eventLogRepository == null && intervalRepository == null && serviceTaskRepository == null) {
            List<EventLogRecord> ev = obj.events() == null ? java.util.List.of() : obj.events();
            List<IntervalRecord> iv = obj.intervals() == null ? java.util.List.of() : obj.intervals();
            List<ServiceTaskRecord> st = obj.serviceTasks() == null ? java.util.List.of() : obj.serviceTasks();
            return new ObjectResponse(obj.id(), obj.name(), obj.type(), obj.status(), obj.createdAt(), obj.updatedAt(),
                    obj.lastChangeDate(), obj.currentValue(), obj.nextServiceDate(), ev, iv, st);
        }
        try {
            ObjectType otype = ObjectType.fromValue(obj.type());
            List<EventLogRecord> events = obj.events() == null ? java.util.List.of() : obj.events();
            List<IntervalRecord> intervals = obj.intervals() == null ? java.util.List.of() : obj.intervals();
            List<ServiceTaskRecord> serviceTasks = obj.serviceTasks() == null ? java.util.List.of()
                    : obj.serviceTasks();

            if (eventLogRepository != null && ObjectType.EVENT == otype) {
                events = eventLogRepository.findByObjectId(UUID.fromString(obj.id()));
                if (events == null)
                    events = java.util.List.of();
            }
            if (intervalRepository != null) {
                List<IntervalRecord> found = intervalRepository.findByObjectId(UUID.fromString(obj.id()));
                intervals = found == null ? java.util.List.of() : found;
            }
            if (serviceTaskRepository != null) {
                List<ServiceTaskRecord> found = serviceTaskRepository.findByObjectId(UUID.fromString(obj.id()));
                serviceTasks = found == null ? java.util.List.of() : found;
            }
            return new ObjectResponse(obj.id(), obj.name(), obj.type(), obj.status(), obj.createdAt(), obj.updatedAt(),
                    obj.lastChangeDate(), obj.currentValue(), obj.nextServiceDate(), events, intervals, serviceTasks);
        } catch (Exception e) {
            // ignore and return original with events/intervals/serviceTasks fixed to empty
            // if null
            List<EventLogRecord> ev = obj.events() == null ? java.util.List.of() : obj.events();
            List<IntervalRecord> iv = obj.intervals() == null ? java.util.List.of() : obj.intervals();
            List<ServiceTaskRecord> st = obj.serviceTasks() == null ? java.util.List.of() : obj.serviceTasks();
            return new ObjectResponse(obj.id(), obj.name(), obj.type(), obj.status(), obj.createdAt(), obj.updatedAt(),
                    obj.lastChangeDate(), obj.currentValue(), obj.nextServiceDate(), ev, iv, st);
        }
    }
}
