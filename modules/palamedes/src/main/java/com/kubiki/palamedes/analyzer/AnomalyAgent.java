package com.kubiki.palamedes.analyzer;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.AnomalyTarget;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AnomalyAgent (MAPE-Analyze):
 * Scans for resources in AnomalyState and creates remediation workflows in State_Initial.
 */
@Service
@RequiredArgsConstructor
public class AnomalyAgent {
    private static final Logger log = LoggerFactory.getLogger(AnomalyAgent.class);

    private final GraphDBGateway gateway;
    private final ActionUtils utils;

    @Scheduled(fixedRate = 5000)
    public void analyze() {
        log.debug("Scanning for cluster anomalies...");
        
        List<AnomalyTarget> anomalies = gateway.findAnomalies();
        
        for (var anomaly : anomalies) {
            log.info("Anomaly detected: resource {} needs {}", anomaly.resourceName(), anomaly.intentIri());
            
            String actionId = utils.generateActionId();
            
            gateway.createActionWorkflow(anomaly.resourceIri(), anomaly.intentIri(), actionId);
        }
    }
}
