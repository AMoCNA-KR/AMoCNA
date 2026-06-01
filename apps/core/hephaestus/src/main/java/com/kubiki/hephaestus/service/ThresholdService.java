package com.kubiki.hephaestus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.kubiki.hephaestus.model.ThresholdDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ThresholdService {
    private static final Logger log = LoggerFactory.getLogger(ThresholdService.class);

    private final String thresholdsPath;
    private final String metricsAdapterUrl;
    private final ObjectMapper yamlMapper;
    private final RestClient restClient;

    public ThresholdService(
            @Value("${amocna.metrics-adapter.thresholds-path}") String thresholdsPath,
            @Value("${amocna.metrics-adapter.url}") String metricsAdapterUrl,
            RestClient.Builder restClientBuilder) {
        this.thresholdsPath = thresholdsPath;
        this.metricsAdapterUrl = metricsAdapterUrl;
        this.restClient = restClientBuilder.build();

        // Configure Jackson YAML mapper with pretty printing
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        this.yamlMapper = new ObjectMapper(yamlFactory);
    }

    /**
     * Resolves the configured thresholds directory.
     */
    private File getThresholdsDir() {
        File dir = new File(thresholdsPath);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), thresholdsPath);
        }
        return dir;
    }

    /**
     * Reads all threshold YAML files from the configured path.
     */
    public List<ThresholdDto> getAllThresholds() throws IOException {
        File dir = getThresholdsDir();
        log.info("Reading thresholds from directory: {}", dir.getAbsolutePath());

        List<ThresholdDto> thresholds = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Thresholds directory does not exist or is not a directory: {}", dir.getAbsolutePath());
            return thresholds;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return thresholds;
        }

        for (File file : files) {
            try {
                ThresholdDto dto = yamlMapper.readValue(file, ThresholdDto.class);
                thresholds.add(dto);
                log.debug("Successfully read threshold file: {}", file.getName());
            } catch (Exception e) {
                log.error("Failed to parse threshold file {}: {}", file.getName(), e.getMessage());
            }
        }
        return thresholds;
    }

    /**
     * Updates/Saves a threshold configuration rule back to disk.
     */
    public void saveThreshold(String filename, ThresholdDto dto) throws IOException {
        File dir = getThresholdsDir();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            log.info("Created missing thresholds directory: {}, success={}", dir.getAbsolutePath(), created);
        }

        File file = new File(dir, filename.endsWith(".yml") || filename.endsWith(".yaml") ? filename : filename + ".yml");
        log.info("Saving threshold config to file: {}", file.getAbsolutePath());
        
        yamlMapper.writeValue(file, dto);
        log.info("Successfully wrote threshold file: {}", file.getName());
    }

    /**
     * Deletes a threshold config file from disk.
     */
    public boolean deleteThreshold(String filename) {
        File dir = getThresholdsDir();
        File file = new File(dir, filename.endsWith(".yml") || filename.endsWith(".yaml") ? filename : filename + ".yml");
        if (file.exists() && file.isFile()) {
            boolean deleted = file.delete();
            log.info("Deleted threshold file: {}, success={}", file.getAbsolutePath(), deleted);
            return deleted;
        }
        return false;
    }

    /**
     * Triggers the dynamic hot-reload endpoint in the metrics-adapter.
     */
    public String triggerMetricsAdapterReload() {
        String reloadUrl = metricsAdapterUrl + "/api/thresholds/reload";
        log.info("Dispatching hot-reload POST trigger to: {}", reloadUrl);
        try {
            return restClient.post()
                    .uri(reloadUrl)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Failed to trigger hot-reload on metrics-adapter: {}", e.getMessage());
            throw new RuntimeException("Metrics-adapter reload failed: " + e.getMessage(), e);
        }
    }
}
