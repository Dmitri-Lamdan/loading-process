package com.loading.process.api;

import com.loading.process.model.CreateObjectRequest;
import com.loading.process.model.CreateEventLogRequest;
import com.loading.process.model.EventLogRecord;
import com.loading.process.model.EventLogResponse;
import com.loading.process.model.ObjectResponse;
import com.loading.process.model.ObjectType;
import com.loading.process.model.ObjectStatus;
import com.loading.process.model.UpdateObjectRequest;
import com.loading.process.model.UserRecord;
import com.loading.process.model.CreateIntervalRequest;
import com.loading.process.model.CreateUserRequest;
import com.loading.process.model.CreateServiceTaskRequest;
import com.loading.process.model.CreateValueHistoryRequest;
import com.loading.process.model.CreateNotificationRequest;
import com.loading.process.model.IntervalRecord;
import com.loading.process.model.ServiceTaskRecord;
import com.loading.process.model.ValueHistoryRecord;
import com.loading.process.model.NotificationRecord;
import com.loading.process.model.NotificationStatus;
import com.loading.process.repository.EventLogRepository;
import com.loading.process.repository.IntervalRepository;
import com.loading.process.repository.ServiceTaskRepository;
import com.loading.process.repository.UserRepository;
import com.loading.process.repository.ValueHistoryRepository;
import com.loading.process.repository.NotificationRepository;
import com.loading.process.service.ObjectBusinessService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class HandleApi {

    private final ObjectBusinessService objectBusinessService;
    private final EventLogRepository eventLogRepository;
    private final IntervalRepository intervalRepository;
    private final ServiceTaskRepository serviceTaskRepository;
    private final UserRepository userRepository;
    private final ValueHistoryRepository valueHistoryRepository;
    private final NotificationRepository notificationRepository;

    public HandleApi() {
        this(new ObjectBusinessService(), null, null, null, null, null, null);
    }

    @Autowired
    public HandleApi(ObjectBusinessService objectBusinessService, EventLogRepository eventLogRepository,
            IntervalRepository intervalRepository, ServiceTaskRepository serviceTaskRepository,
            UserRepository userRepository, ValueHistoryRepository valueHistoryRepository,
            NotificationRepository notificationRepository) {
        this.objectBusinessService = objectBusinessService;
        this.eventLogRepository = eventLogRepository;
        this.intervalRepository = intervalRepository;
        this.serviceTaskRepository = serviceTaskRepository;
        this.userRepository = userRepository;
        this.valueHistoryRepository = valueHistoryRepository;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/objects")
    public <T> ResponseEntity<List<T>> getObjects(
            @RequestParam(required = false) ObjectType type,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(objectBusinessService.getObjects(type, parseObjectStatus(status)));
    }

    public <T> ResponseEntity<List<T>> getObjects(String type, String status) {
        return getObjects(type == null || type.isBlank() ? null : ObjectType.fromValue(type), status);
    }

    private ObjectStatus parseObjectStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ObjectStatus.fromValue(status);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "status must be one of: active, inactive", exception);
        }
    }

    @GetMapping("/objects/{id}")
    public ResponseEntity<ObjectResponse> getObject(@PathVariable String id) {
        return ResponseEntity.ok(objectBusinessService.getObject(id));
    }

    @PostMapping("/objects")
    public ResponseEntity<ObjectResponse> createObject(@RequestBody CreateObjectRequest request) {
        ObjectResponse response = objectBusinessService.createObject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/objects/{id}")
    public ResponseEntity<ObjectResponse> updateObject(@PathVariable String id,
            @RequestBody UpdateObjectRequest request) {
        return ResponseEntity.ok(objectBusinessService.updateObject(id, request));
    }

    @DeleteMapping("/objects/{id}")
    public ResponseEntity<Void> deleteObject(@PathVariable String id) {
        objectBusinessService.deleteObject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/event-logs/{id}")
    public ResponseEntity<EventLogResponse> getEventLog(@PathVariable UUID id) {
        requireEventLogRepository();
        EventLogRecord event = eventLogRepository.findById(id);
        if (event == null) {
            return ResponseEntity.notFound().build();
        }
        ObjectResponse object = null;
        try {
            object = objectBusinessService.getObject(event.objectId().toString());
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            // object may not exist; leave null
        }
        UserRecord user = null;
        if (userRepository != null && event.userId() != null) {
            user = userRepository.findById(event.userId());
        }
        return ResponseEntity.ok(new EventLogResponse(event, object, user));
    }

    @GetMapping("/event-logs")
    public ResponseEntity<List<EventLogResponse>> getEventLogs(@RequestParam UUID objectId) {
        requireEventLogRepository();
        List<EventLogRecord> events = eventLogRepository.findByObjectId(objectId);
        List<EventLogResponse> responses = events.stream().map(e -> {
            ObjectResponse object = null;
            try {
                object = objectBusinessService.getObject(e.objectId().toString());
            } catch (org.springframework.web.server.ResponseStatusException ex) {
            }
            UserRecord user = null;
            if (userRepository != null && e.userId() != null) {
                user = userRepository.findById(e.userId());
            }
            return new EventLogResponse(e, object, user);
        }).toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/event-logs")
    public ResponseEntity<EventLogResponse> createEventLog(@RequestBody CreateEventLogRequest request) {
        requireEventLogRepository();
        if (request == null || request.objectId() == null || request.eventType() == null
                || request.eventType().isBlank() || request.message() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId, eventType and message are required");
        }
        EventLogRecord event = new EventLogRecord(
                UUID.randomUUID(),
                request.objectId(),
                request.userId(),
                request.timestamp() == null ? LocalDateTime.now() : request.timestamp(),
                request.eventType().trim(),
                request.message());
        EventLogRecord saved = eventLogRepository.save(event);
        ObjectResponse object = null;
        try {
            object = objectBusinessService.getObject(saved.objectId().toString());
        } catch (org.springframework.web.server.ResponseStatusException ex) {
        }
        UserRecord user = null;
        if (userRepository != null && saved.userId() != null) {
            user = userRepository.findById(saved.userId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(new EventLogResponse(saved, object, user));
    }

    @PutMapping("/event-logs/{id}")
    public ResponseEntity<EventLogResponse> updateEventLog(
            @PathVariable UUID id, @RequestBody CreateEventLogRequest request) {
        requireEventLogRepository();
        if (request == null || request.objectId() == null || request.eventType() == null
                || request.eventType().isBlank() || request.message() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId, eventType and message are required");
        }
        if (eventLogRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        EventLogRecord event = new EventLogRecord(
                id,
                request.objectId(),
                request.userId(),
                request.timestamp() == null ? LocalDateTime.now() : request.timestamp(),
                request.eventType().trim(),
                request.message());
        EventLogRecord updated = eventLogRepository.update(event);
        ObjectResponse object = null;
        try {
            object = objectBusinessService.getObject(updated.objectId().toString());
        } catch (org.springframework.web.server.ResponseStatusException ex) {
        }
        UserRecord user = null;
        if (userRepository != null && updated.userId() != null) {
            user = userRepository.findById(updated.userId());
        }
        return ResponseEntity.ok(new EventLogResponse(updated, object, user));
    }

    @DeleteMapping("/event-logs/{id}")
    public ResponseEntity<Void> deleteEventLog(@PathVariable UUID id) {
        requireEventLogRepository();
        if (eventLogRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        eventLogRepository.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Intervals endpoints
    private void requireIntervalRepository() {
        if (intervalRepository == null) {
            throw new IllegalStateException("IntervalRepository is not configured");
        }
    }

    @GetMapping("/intervals/{id}")
    public ResponseEntity<IntervalRecord> getInterval(@PathVariable UUID id) {
        requireIntervalRepository();
        IntervalRecord interval = intervalRepository.findById(id);
        if (interval == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(interval);
    }

    @GetMapping("/intervals")
    public ResponseEntity<List<IntervalRecord>> getIntervals(@RequestParam UUID objectId) {
        requireIntervalRepository();
        List<IntervalRecord> intervals = intervalRepository.findByObjectId(objectId);
        return ResponseEntity.ok(intervals);
    }

    @PostMapping("/intervals")
    public ResponseEntity<IntervalRecord> createInterval(@RequestBody CreateIntervalRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.objectId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId is required");
        }
        if (request.intervalUnit() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "intervalUnit is required");
        }
        requireIntervalRepository();
        IntervalRecord interval = new IntervalRecord(
                java.util.UUID.randomUUID(),
                request.objectId(),
                request.intervalValue(),
                request.intervalUnit(),
                request.createdAt() == null ? LocalDateTime.now() : request.createdAt());
        IntervalRecord saved = intervalRepository.save(interval);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/intervals/{id}")
    public ResponseEntity<IntervalRecord> updateInterval(@PathVariable UUID id,
            @RequestBody CreateIntervalRequest request) {
        requireIntervalRepository();
        if (request == null || request.objectId() == null || request.intervalUnit() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId and intervalUnit are required");
        }
        if (intervalRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        IntervalRecord interval = new IntervalRecord(
                id,
                request.objectId(),
                request.intervalValue(),
                request.intervalUnit(),
                request.createdAt() == null ? LocalDateTime.now() : request.createdAt());
        IntervalRecord updated = intervalRepository.update(interval);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/intervals/{id}")
    public ResponseEntity<Void> deleteInterval(@PathVariable UUID id) {
        requireIntervalRepository();
        if (intervalRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        intervalRepository.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Users endpoints
    private void requireUserRepository() {
        if (userRepository == null) {
            throw new IllegalStateException("UserRepository is not configured");
        }
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserRecord> getUser(@PathVariable UUID id) {
        requireUserRepository();
        UserRecord user = userRepository.findById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserRecord>> getUsers() {
        requireUserRepository();
        List<UserRecord> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    public ResponseEntity<UserRecord> createUser(@RequestBody CreateUserRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "username is required");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "email is required");
        }
        if (request.role() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "role is required");
        }
        requireUserRepository();

        UserRecord user = new UserRecord(
                java.util.UUID.randomUUID(),
                request.username().trim(),
                request.email().trim(),
                request.role());
        UserRecord saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserRecord> updateUser(@PathVariable UUID id, @RequestBody CreateUserRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "username is required");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "email is required");
        }
        if (request.role() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "role is required");
        }
        requireUserRepository();

        if (userRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }

        UserRecord user = new UserRecord(
                id,
                request.username().trim(),
                request.email().trim(),
                request.role());
        UserRecord updated = userRepository.update(user);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        requireUserRepository();
        if (userRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        userRepository.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ServiceTasks endpoints
    private void requireServiceTaskRepository() {
        if (serviceTaskRepository == null) {
            throw new IllegalStateException("ServiceTaskRepository is not configured");
        }
    }

    @GetMapping("/service-tasks/{id}")
    public ResponseEntity<ServiceTaskRecord> getServiceTask(@PathVariable UUID id) {
        requireServiceTaskRepository();
        ServiceTaskRecord task = serviceTaskRepository.findById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    @GetMapping("/service-tasks")
    public ResponseEntity<List<ServiceTaskRecord>> getServiceTasks(@RequestParam UUID objectId) {
        requireServiceTaskRepository();
        List<ServiceTaskRecord> tasks = serviceTaskRepository.findByObjectId(objectId);
        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/service-tasks")
    public ResponseEntity<ServiceTaskRecord> createServiceTask(@RequestBody CreateServiceTaskRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.objectId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId is required");
        }
        if (request.plannedDate() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "plannedDate is required");
        }
        requireServiceTaskRepository();

        ServiceTaskRecord task = new ServiceTaskRecord(
                java.util.UUID.randomUUID(),
                request.objectId(),
                request.plannedDate(),
                request.completedDate(),
                request.status() == null ? "planned" : request.status(),
                request.comment());
        ServiceTaskRecord saved = serviceTaskRepository.save(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/service-tasks/{id}")
    public ResponseEntity<ServiceTaskRecord> updateServiceTask(@PathVariable UUID id,
            @RequestBody CreateServiceTaskRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.objectId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId is required");
        }
        if (request.plannedDate() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "plannedDate is required");
        }
        requireServiceTaskRepository();

        if (serviceTaskRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }

        ServiceTaskRecord task = new ServiceTaskRecord(
                id,
                request.objectId(),
                request.plannedDate(),
                request.completedDate(),
                request.status() == null ? "planned" : request.status(),
                request.comment());
        ServiceTaskRecord updated = serviceTaskRepository.update(task);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/service-tasks/{id}")
    public ResponseEntity<Void> deleteServiceTask(@PathVariable UUID id) {
        requireServiceTaskRepository();
        if (serviceTaskRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        serviceTaskRepository.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ValueHistory endpoints
    private void requireValueHistoryRepository() {
        if (valueHistoryRepository == null) {
            throw new IllegalStateException("ValueHistoryRepository is not configured");
        }
    }

    @GetMapping("/value-histories/{id}")
    public ResponseEntity<ValueHistoryRecord> getValueHistory(@PathVariable UUID id) {
        requireValueHistoryRepository();
        ValueHistoryRecord history = valueHistoryRepository.findById(id.toString());
        if (history == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(history);
    }

    @GetMapping("/value-histories")
    public ResponseEntity<List<ValueHistoryRecord>> getValueHistories(@RequestParam UUID objectId) {
        requireValueHistoryRepository();
        List<ValueHistoryRecord> histories = valueHistoryRepository.findByObjectId(objectId.toString());
        return ResponseEntity.ok(histories);
    }

    @PostMapping("/value-histories")
    public ResponseEntity<ValueHistoryRecord> createValueHistory(@RequestBody CreateValueHistoryRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.objectId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId is required");
        }
        if (request.value() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "value is required");
        }
        requireValueHistoryRepository();

        ValueHistoryRecord history = new ValueHistoryRecord(
                java.util.UUID.randomUUID(),
                request.objectId(),
                request.timestamp() == null ? LocalDateTime.now() : request.timestamp(),
                request.value());
        ValueHistoryRecord saved = valueHistoryRepository.save(history);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/value-histories/{id}")
    public ResponseEntity<ValueHistoryRecord> updateValueHistory(@PathVariable UUID id,
            @RequestBody CreateValueHistoryRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.objectId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId is required");
        }
        if (request.value() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "value is required");
        }
        requireValueHistoryRepository();

        if (valueHistoryRepository.findById(id.toString()) == null) {
            return ResponseEntity.notFound().build();
        }

        ValueHistoryRecord history = new ValueHistoryRecord(
                id,
                request.objectId(),
                request.timestamp() == null ? LocalDateTime.now() : request.timestamp(),
                request.value());
        ValueHistoryRecord updated = valueHistoryRepository.update(history);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/value-histories/{id}")
    public ResponseEntity<Void> deleteValueHistory(@PathVariable UUID id) {
        requireValueHistoryRepository();
        if (valueHistoryRepository.findById(id.toString()) == null) {
            return ResponseEntity.notFound().build();
        }
        valueHistoryRepository.delete(id.toString());
        return ResponseEntity.noContent().build();
    }

    // Notification endpoints
    private void requireNotificationRepository() {
        if (notificationRepository == null) {
            throw new IllegalStateException("NotificationRepository is not configured");
        }
    }

    @GetMapping("/notifications/{id}")
    public ResponseEntity<NotificationRecord> getNotification(@PathVariable UUID id) {
        requireNotificationRepository();
        NotificationRecord notification = notificationRepository.findById(id);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(notification);
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationRecord>> getNotifications(
            @RequestParam(required = false) UUID objectId,
            @RequestParam(required = false) UUID userId) {
        requireNotificationRepository();
        List<NotificationRecord> notifications;
        if (objectId != null) {
            notifications = notificationRepository.findByObjectId(objectId);
        } else if (userId != null) {
            notifications = notificationRepository.findByUserId(userId);
        } else {
            notifications = notificationRepository.findAll();
        }
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/notifications")
    public ResponseEntity<NotificationRecord> createNotification(@RequestBody CreateNotificationRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.objectId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId is required");
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "message is required");
        }
        requireNotificationRepository();

        NotificationRecord notification = new NotificationRecord(
                java.util.UUID.randomUUID(),
                request.objectId(),
                request.userId(),
                LocalDateTime.now(),
                request.message(),
                request.status() == null ? NotificationStatus.PENDING : request.status(),
                request.isRead(),
                null);
        NotificationRecord saved = notificationRepository.save(notification);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/notifications/{id}")
    public ResponseEntity<NotificationRecord> updateNotification(@PathVariable UUID id,
            @RequestBody CreateNotificationRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.objectId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "objectId is required");
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "message is required");
        }
        requireNotificationRepository();

        NotificationRecord existing = notificationRepository.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        NotificationRecord notification = new NotificationRecord(
                id,
                request.objectId(),
                request.userId(),
                LocalDateTime.now(),
                request.message(),
                request.status() == null ? existing.status() : request.status(),
                request.isRead(),
                null);
        NotificationRecord updated = notificationRepository.update(notification);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable UUID id) {
        requireNotificationRepository();
        if (notificationRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        notificationRepository.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void requireEventLogRepository() {
        if (eventLogRepository == null) {
            throw new IllegalStateException("EventLogRepository is not configured");
        }
    }
}
