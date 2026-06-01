package com.kubiki.hephaestus.controller;

import com.kubiki.hephaestus.model.ThresholdDto;
import com.kubiki.hephaestus.service.ThresholdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/thresholds")
@RequiredArgsConstructor
public class ThresholdController {

    private final ThresholdService thresholdService;

    /**
     * Lists all metric thresholds configured in metrics-adapter.
     */
    @GetMapping
    public ResponseEntity<List<ThresholdDto>> listThresholds() {
        try {
            return ResponseEntity.ok(thresholdService.getAllThresholds());
        } catch (IOException e) {
            log.error("Failed to retrieve threshold configurations: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Saves or updates a specific threshold rule file on disk.
     */
    @PutMapping("/{filename}")
    public ResponseEntity<String> updateThreshold(
            @PathVariable String filename,
            @RequestBody ThresholdDto dto) {
        try {
            thresholdService.saveThreshold(filename, dto);
            
            // Automatically trigger reload on the metrics-adapter
            try {
                String reloadResult = thresholdService.triggerMetricsAdapterReload();
                return ResponseEntity.ok("Threshold updated and metrics-adapter hot-reloaded: " + reloadResult);
            } catch (Exception reloadEx) {
                log.warn("Threshold written successfully, but hot-reload trigger failed: {}", reloadEx.getMessage());
                return ResponseEntity.ok("Threshold updated on disk, but metrics-adapter hot-reload failed: " + reloadEx.getMessage());
            }
            
        } catch (IOException e) {
            log.error("Failed to save threshold file {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().body("Failed to write file: " + e.getMessage());
        }
    }

    /**
     * Deletes a threshold rule file from disk.
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<String> deleteThreshold(@PathVariable String filename) {
        boolean deleted = thresholdService.deleteThreshold(filename);
        if (deleted) {
            try {
                String reloadResult = thresholdService.triggerMetricsAdapterReload();
                return ResponseEntity.ok("Threshold deleted and metrics-adapter reloaded: " + reloadResult);
            } catch (Exception reloadEx) {
                return ResponseEntity.ok("Threshold deleted from disk, but metrics-adapter hot-reload failed: " + reloadEx.getMessage());
            }
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Explicitly triggers the hot-reload scanner refresh on the metrics-adapter.
     */
    @PostMapping("/reload")
    public ResponseEntity<String> reloadThresholds() {
        try {
            String result = thresholdService.triggerMetricsAdapterReload();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hot-reload trigger failed: " + e.getMessage());
        }
    }
}
