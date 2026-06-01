package com.kubiki.hephaestus.service;

import com.kubiki.hephaestus.model.ThresholdDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ThresholdServiceTest {

    private ThresholdService thresholdService;
    private RestClient.Builder restClientBuilder;
    private RestClient restClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        restClientBuilder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        when(restClientBuilder.build()).thenReturn(restClient);

        // Point thresholds path to our temporary directory to avoid dirtying source code
        thresholdService = new ThresholdService(
                tempDir.toAbsolutePath().toString(),
                "http://localhost:8085",
                restClientBuilder
        );
    }

    @Test
    void testSaveAndGetThresholds() throws IOException {
        ThresholdDto dto = new ThresholdDto(
                "temp-rule",
                "up == 0",
                "==",
                0.0,
                "NetworkPartitionState",
                "Node",
                "instance",
                null,
                0
        );

        // Save rule
        thresholdService.saveThreshold("temp-rule", dto);

        // Verify file written to TempDir
        File file = tempDir.resolve("temp-rule.yml").toFile();
        assertTrue(file.exists());

        // Get all rules and assert contents
        List<ThresholdDto> list = thresholdService.getAllThresholds();
        assertEquals(1, list.size());
        assertEquals("temp-rule", list.get(0).name());
        assertEquals("up == 0", list.get(0).query());
        assertEquals("==", list.get(0).operator());
        assertEquals(0.0, list.get(0).value());
    }

    @Test
    void testDeleteThreshold() throws IOException {
        // Pre-create file
        Path rulePath = tempDir.resolve("delete-me.yml");
        Files.writeString(rulePath, "name: delete-me\nquery: up\noperator: '>'\nvalue: 0\nanomalyState: State\nresourceKind: Pod\nresourceLabel: pod");

        assertTrue(rulePath.toFile().exists());

        // Delete
        boolean deleted = thresholdService.deleteThreshold("delete-me");
        assertTrue(deleted);
        assertFalse(rulePath.toFile().exists());
    }

    @Test
    void testTriggerMetricsAdapterReloadSuccess() {
        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("Reloaded successfully");

        String result = thresholdService.triggerMetricsAdapterReload();
        assertEquals("Reloaded successfully", result);
    }
}
