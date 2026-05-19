package com.kubiki.palamedes.prometheus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.kubiki.palamedes.config.PalamedesProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads threshold definitions from palamedes/src/main/resources/thresholds/*.yml
 * and injects them into PalamedesProperties.
 */
@Component
@RequiredArgsConstructor
public class ThresholdsLoader {
    private static final Logger log = LoggerFactory.getLogger(ThresholdsLoader.class);
    private final PalamedesProperties properties;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @PostConstruct
    public void loadThresholds() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:thresholds/*.yml");
        
        List<ThresholdDefinition> thresholds = new ArrayList<>();
        for (Resource resource : resources) {
            try {
                ThresholdDefinition threshold = yamlMapper.readValue(resource.getInputStream(), ThresholdDefinition.class);
                thresholds.add(threshold);
                log.info("Loaded threshold definition: {} from {}", threshold.name(), resource.getFilename());
            } catch (Exception e) {
                log.error("Failed to load threshold from {}: {}", resource.getFilename(), e.getMessage());
            }
        }
        
        properties.getThresholds().addAll(thresholds);
        log.info("Total {} threshold(s) loaded into PalamedesProperties", thresholds.size());
    }
}
