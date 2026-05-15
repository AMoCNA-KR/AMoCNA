package com.kubiki.palamedes.analyzer;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.OntologyRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * AnomalyAgent (MAPE-Analyze):
 * Scans for resources in AnomalyState and creates remediation workflows in State_Initial.
 */
@Service
public class AnomalyAgent {
    private static final Logger log = LoggerFactory.getLogger(AnomalyAgent.class);
    private final GraphDBGateway gateway;

    public AnomalyAgent(GraphDBGateway gateway) {
        this.gateway = gateway;
    }

    @Scheduled(fixedRate = 5000)
    public void analyze() {
        log.debug("AnomalyAgent: Scanning for cluster anomalies...");
        
        List<GraphDBGateway.AnomalyTarget> anomalies = gateway.findAnomalies();
        
        for (GraphDBGateway.AnomalyTarget anomaly : anomalies) {
            log.info("Anomaly detected: resource {} needs {}", anomaly.resourceName(), anomaly.intentIri());
            
            // Create a unique ID for this specific remediation attempt
            String actionId = "action-" + UUID.randomUUID().toString().substring(0, 8);
            
            gateway.createActionWorkflow(anomaly.resourceIri(), anomaly.intentIri(), actionId);
        }
    }
}
