package com.loading.process.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loading.process.model.CreateObjectRequest;
import com.loading.process.model.ObjectResponse;
import com.loading.process.model.ObjectStatus;
import com.loading.process.model.UpdateObjectRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ObjectControllerTest {

        @Test
        void createObject_shouldReturnCreatedObject() {
                HandleApi controller = new HandleApi();

                CreateObjectRequest request = new CreateObjectRequest(
                                "Двигатель автомобиля",
                                "calendar",
                                ObjectStatus.ACTIVE,
                                BigDecimal.ZERO,
                                LocalDate.of(2026, 8, 15));

                ResponseEntity<ObjectResponse> response = controller.createObject(request);

                assertEquals(HttpStatus.CREATED, response.getStatusCode());
                assertNotNull(response.getBody());
                assertNotNull(response.getBody().id());
                assertEquals(7, UUID.fromString(response.getBody().id()).version());
                assertEquals("Двигатель автомобиля", response.getBody().name());
                assertEquals("calendar", response.getBody().type());
                assertEquals(ObjectStatus.ACTIVE, response.getBody().status());
                assertTrue(response.getBody().currentValue().compareTo(BigDecimal.ZERO) >= 0);
        }

        @Test
        void createObject_shouldDefaultStatusToActive() {
                HandleApi controller = new HandleApi();
                CreateObjectRequest request = new CreateObjectRequest(
                                "Water filter", "other", (ObjectStatus) null, BigDecimal.ZERO, null);

                ResponseEntity<ObjectResponse> response = controller.createObject(request);

                assertEquals(ObjectStatus.ACTIVE, response.getBody().status());
        }

        @Test
        void getObjects_shouldReturnFilteredList() {
                HandleApi controller = new HandleApi();

                controller
                                .createObject(new CreateObjectRequest("First", "calendar", ObjectStatus.ACTIVE,
                                                BigDecimal.ONE, LocalDate.now()));
                controller.createObject(
                                new CreateObjectRequest("Second", "mileage", ObjectStatus.INACTIVE,
                                                new BigDecimal("10.5"), LocalDate.now()));

                ResponseEntity<List<ObjectResponse>> response = controller.getObjects("calendar", "active");

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(1, response.getBody().size());
                assertEquals("First", response.getBody().getFirst().name());
        }

        @Test
        void updateObject_shouldRecalculateAndPersistValues() {
                HandleApi controller = new HandleApi();
                String id = controller
                                .createObject(new CreateObjectRequest("Brake pads", "calendar", ObjectStatus.ACTIVE,
                                                BigDecimal.ZERO,
                                                LocalDate.of(2026, 8, 15)))
                                .getBody().id();

                UpdateObjectRequest updateRequest = new UpdateObjectRequest(
                                "Brake pads updated",
                                "mileage",
                                ObjectStatus.INACTIVE,
                                new BigDecimal("120.75"),
                                LocalDate.of(2026, 9, 10));

                ResponseEntity<ObjectResponse> response = controller.updateObject(id, updateRequest);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals("Brake pads updated", response.getBody().name());
                assertEquals("mileage", response.getBody().type());
                assertEquals(ObjectStatus.INACTIVE, response.getBody().status());
                assertEquals(new BigDecimal("120.75"), response.getBody().currentValue());
        }

        @Test
        void updateObject_shouldRejectUnknownId() {
                HandleApi controller = new HandleApi();
                UpdateObjectRequest updateRequest = new UpdateObjectRequest("Missing", "calendar", ObjectStatus.ACTIVE,
                                BigDecimal.ONE,
                                LocalDate.now());

                assertThrows(ResponseStatusException.class,
                                () -> controller.updateObject(UUID.randomUUID().toString(), updateRequest));
        }
}
