package com.kubiki.palamedes.analyzer;

import com.kubiki.palamedes.planner.PlannerService;
import com.kubiki.palamedes.dispatcher.DispatcherService;
import com.kubiki.palamedes.model.ActionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AnalyzerService {
    private static final Logger log = LoggerFactory.getLogger(AnalyzerService.class);
    private final PlannerService plannerService;
    private final DispatcherService dispatcherService;

    public AnalyzerService(PlannerService plannerService, DispatcherService dispatcherService) {
        this.plannerService = plannerService;
        this.dispatcherService = dispatcherService;
    }

    @Scheduled(fixedRate = 5000)
    public void detectAnomalies() {
        log.debug("Checking for cluster anomalies...");
        
        // 1. Find resources with AnomalyState in CNEEOnt
        // 2. Map to FunctionalIntent via BridgeOnt (isResolvedByIntent)
        
        // Example SPARQL query logic:
        // SELECT ?resourceName ?intentIri WHERE {
        //   ?resource cnee:hasCurrentState ?state .
        //   ?state a cnee:AnomalyState .
        //   ?resource cnee:resourceName ?resourceName .
        //   ?state bridge:isResolvedByIntent ?intentIri .
        // }
        
        // Mocking an anomaly detection for a pod
        String resourceName = "frontend-pod";
        String intentIri = "http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#RestartAction";
        
        log.info("Anomaly detected: {} requires {}", resourceName, intentIri);
        ActionMessage message = plannerService.buildActionMessage(resourceName, intentIri);
        dispatcherService.dispatch(message);
    }
}
