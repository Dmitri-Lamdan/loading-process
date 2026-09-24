package com.loading.process.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.loading.process.model.CreateNotificationRequest;
import com.loading.process.model.NotificationRecord;
import com.loading.process.model.NotificationStatus;
import com.loading.process.repository.NotificationRepository;
import com.loading.process.service.ObjectBusinessService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class NotificationStatusTest {

    @Test
    void createNotificationDefaultsStatusToPending() {
        NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.save(any(NotificationRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        HandleApi controller = controllerWith(repository);
        CreateNotificationRequest request = request(null);

        ResponseEntity<NotificationRecord> response = controller.createNotification(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(NotificationStatus.PENDING, response.getBody().status());
    }

    @Test
    void createNotificationUsesRequestedStatus() {
        NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.save(any(NotificationRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        HandleApi controller = controllerWith(repository);

        ResponseEntity<NotificationRecord> response = controller.createNotification(request(NotificationStatus.SENT));

        assertEquals(NotificationStatus.SENT, response.getBody().status());
    }

    @Test
    void updateNotificationPreservesStatusWhenNotProvided() {
        NotificationRepository repository = mock(NotificationRepository.class);
        UUID id = UUID.randomUUID();
        NotificationRecord existing = new NotificationRecord(id, UUID.randomUUID(), null,
                LocalDateTime.now(), "Existing reminder", NotificationStatus.SENT, false, null);
        when(repository.findById(id)).thenReturn(existing);
        when(repository.update(any(NotificationRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        HandleApi controller = controllerWith(repository);

        ResponseEntity<NotificationRecord> response = controller.updateNotification(id, request(null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(NotificationStatus.SENT, response.getBody().status());
    }

    private HandleApi controllerWith(NotificationRepository repository) {
        return new HandleApi(new ObjectBusinessService(), null, null, null, null, null, repository);
    }

    private CreateNotificationRequest request(NotificationStatus status) {
        return new CreateNotificationRequest(UUID.randomUUID(), null, "Replace the water filter", status, false);
    }
}