package com.loading.process.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.loading.process.model.ObjectResponse;
import com.loading.process.service.ObjectBusinessService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpControllerTest {

    @Test
    void shouldListObjectsThroughStandardToolMethod() {
        McpController controller = new McpController(new ObjectBusinessService());

        List<ObjectResponse> objects = controller.listObjects(null, null);

        assertNotNull(objects);
    }

    @Test
    void shouldCreateAndDeleteObjectThroughStandardToolMethods() {
        McpController controller = new McpController(new ObjectBusinessService());

        ObjectResponse created = controller.createObject(
                "MCP object", "other", null, null, null);

        assertNotNull(created.id());
        assertEquals(Map.of("deleted", true, "id", created.id()), controller.deleteObject(created.id()));
    }

    @Test
    void shouldFetchObjectThroughStandardToolMethod() {
        McpController controller = new McpController(new ObjectBusinessService());
        ObjectResponse created = controller.createObject("Fetch me", "other", null, null, null);

        assertEquals(created, controller.getObject(created.id()));
    }
}
