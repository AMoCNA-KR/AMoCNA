package com.kubiki.palamedes.planner;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannerServiceTest {
    private final PlannerService plannerService = new PlannerService();

    @Test
    void shouldHydrateUrl() {
        String template = "https://k8s/{resourceName}";
        Map<String, String> data = Map.of("resourceName", "my-pod");
        String result = plannerService.hydrate(template, data);
        assertEquals("https://k8s/my-pod", result);
    }

    @Test
    void shouldHandleNullData() {
        String template = "https://k8s/no-data";
        String result = plannerService.hydrate(template, null);
        assertEquals(template, result);
    }
}
