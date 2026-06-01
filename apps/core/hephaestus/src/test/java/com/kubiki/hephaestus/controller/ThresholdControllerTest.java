package com.kubiki.hephaestus.controller;

import com.kubiki.hephaestus.model.ThresholdDto;
import com.kubiki.hephaestus.service.ThresholdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ThresholdControllerTest {

    private ThresholdController thresholdController;
    private ThresholdService thresholdService;

    @BeforeEach
    void setUp() {
        thresholdService = mock(ThresholdService.class);
        thresholdController = new ThresholdController(thresholdService);
    }

    @Test
    void testListThresholdsSuccess() throws IOException {
        ThresholdDto dto = new ThresholdDto("test", "up", ">", 0.0, "State", "Node", "inst", null, 0);
        when(thresholdService.getAllThresholds()).thenReturn(Collections.singletonList(dto));

        ResponseEntity<List<ThresholdDto>> response = thresholdController.listThresholds();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("test", response.getBody().get(0).name());
    }

    @Test
    void testListThresholdsError() throws IOException {
        when(thresholdService.getAllThresholds()).thenThrow(new IOException("Disk read error"));

        ResponseEntity<List<ThresholdDto>> response = thresholdController.listThresholds();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testUpdateThresholdSuccess() throws IOException {
        ThresholdDto dto = new ThresholdDto("test", "up", ">", 0.0, "State", "Node", "inst", null, 0);
        doNothing().when(thresholdService).saveThreshold(anyString(), any(ThresholdDto.class));
        when(thresholdService.triggerMetricsAdapterReload()).thenReturn("Reload successful");

        ResponseEntity<String> response = thresholdController.updateThreshold("test", dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(thresholdService, times(1)).saveThreshold("test", dto);
        verify(thresholdService, times(1)).triggerMetricsAdapterReload();
    }

    @Test
    void testDeleteThresholdSuccess() {
        when(thresholdService.deleteThreshold("test")).thenReturn(true);
        when(thresholdService.triggerMetricsAdapterReload()).thenReturn("Reload successful");

        ResponseEntity<String> response = thresholdController.deleteThreshold("test");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(thresholdService, times(1)).deleteThreshold("test");
    }

    @Test
    void testDeleteThresholdNotFound() {
        when(thresholdService.deleteThreshold("test")).thenReturn(false);

        ResponseEntity<String> response = thresholdController.deleteThreshold("test");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(thresholdService, times(1)).deleteThreshold("test");
        verify(thresholdService, never()).triggerMetricsAdapterReload();
    }
}
