package com.kubiki.metrics.controller;

import com.kubiki.metrics.prometheus.ThresholdsLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Controller exposing endpoints to trigger actions on thresholds at runtime.
 */
@Slf4j
@RestController
@RequestMapping("/api/thresholds")
@RequiredArgsConstructor
public class ThresholdController {

    private final ThresholdsLoader thresholdsLoader;

    /**
     * Clears the loaded thresholds in memory and reloads them from the YAML folder dynamically.
     */
    @PostMapping("/reload")
    public String reloadThresholds() throws IOException {
        log.info("Hot-reload triggered: clearing and reloading thresholds from classpath...");
        
        // Access loadedThresholds directly and clear it
        thresholdsLoader.getThresholds().clear();
        
        // Re-read file system configurations
        thresholdsLoader.loadThresholds();
        
        int totalLoaded = thresholdsLoader.getThresholds().size();
        log.info("Hot-reload finished successfully. Loaded {} thresholds.", totalLoaded);
        return "Thresholds reloaded successfully. Total loaded: " + totalLoaded;
    }
}
