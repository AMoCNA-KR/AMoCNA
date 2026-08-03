package com.kubiki.palamedes.scig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.common.vulnerability.ScigRedisClient;
import com.kubiki.common.vulnerability.UpgradePolicy;
import com.kubiki.common.vulnerability.VulnerabilityCatalog;
import com.kubiki.common.vulnerability.VulnerabilityRecord;
import com.kubiki.palamedes.config.PalamedesProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronizes Grype/Syft vulnerability scan reports stored by SCIG in Redis into Palamedes VulnerabilityCatalog.
 */
@Service
@RequiredArgsConstructor
public class ScigRedisSyncService {

    private static final Logger log = LoggerFactory.getLogger(ScigRedisSyncService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final VulnerabilityCatalog vulnerabilityCatalog;
    private final PalamedesProperties palamedesProperties;

    @Value("${scig.redis.host:redis.redis.svc.cluster.local}")
    private String redisHost;

    @Value("${scig.redis.port:6379}")
    private int redisPort;

    public void syncFromRedis() {
        ScigRedisClient client = new ScigRedisClient(redisHost, redisPort);
        if (!client.ping()) {
            log.debug("ScigRedisSyncService: Redis at {}:{} not reachable. Skipping dynamic SCIG sync.", redisHost, redisPort);
            return;
        }

        List<String> cveKeys = client.scanKeys("sbom:cve:*");
        if (cveKeys.isEmpty()) {
            log.debug("ScigRedisSyncService: No sbom:cve:* keys found in Redis.");
            return;
        }

        List<VulnerabilityRecord> dynamicRecords = new ArrayList<>();
        for (String key : cveKeys) {
            String json = client.get(key);
            if (json == null || json.isBlank()) {
                continue;
            }

            // Key format: sbom:cve:{repository}:{tag}
            String[] parts = key.split(":");
            if (parts.length < 4) {
                continue;
            }
            String repo = parts[2];
            String tag = parts[3];

            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode matches = root.get("matches");
                if (matches != null && matches.isArray()) {
                    for (JsonNode match : matches) {
                        JsonNode vuln = match.get("vulnerability");
                        if (vuln == null) continue;

                        String cveId = vuln.has("id") ? vuln.get("id").asText() : "UNKNOWN";
                        String severity = vuln.has("severity") ? vuln.get("severity").asText() : "Medium";

                        List<String> fixedVersions = new ArrayList<>();
                        JsonNode fixes = vuln.get("fix");
                        if (fixes != null && fixes.has("versions") && fixes.get("versions").isArray()) {
                            for (JsonNode v : fixes.get("versions")) {
                                fixedVersions.add(v.asText());
                            }
                        }

                        VulnerabilityRecord record = new VulnerabilityRecord(
                                cveId,
                                repo,
                                List.of(tag),
                                fixedVersions,
                                severity,
                                UpgradePolicy.PATCH
                        );
                        dynamicRecords.add(record);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse SCIG CVE JSON from Redis key {}: {}", key, e.getMessage());
            }
        }

        if (!dynamicRecords.isEmpty()) {
            vulnerabilityCatalog.mergeRecords(dynamicRecords);
            log.info("ScigRedisSyncService: Successfully synced {} CVE records from SCIG Redis scan reports", dynamicRecords.size());
        }
    }
}
