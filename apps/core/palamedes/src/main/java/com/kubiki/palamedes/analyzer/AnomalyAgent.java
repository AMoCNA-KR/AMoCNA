package com.kubiki.palamedes.analyzer;

import com.kubiki.palamedes.analyzer.hydration.ActionHydrator;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.AnomalyTarget;
import com.kubiki.palamedes.pipeline.EngineWakeupEvent;
import com.kubiki.palamedes.reasoner.RcaEngine;
import com.kubiki.palamedes.service.RemediationFilterService;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AnomalyAgent (MAPE-Analyze):
 * Scans for resources in AnomalyState and creates remediation workflows.
 * Refactored using Strategy pattern for hydration and clean code principles.
 */
@Service
@RequiredArgsConstructor
public class AnomalyAgent {
    private static final Logger log = LoggerFactory.getLogger(AnomalyAgent.class);

    private final GraphDBGateway gateway;
    private final RcaEngine rcaEngine;
    private final ActionUtils utils;
    private final ApplicationEventPublisher publisher;
    private final AnomalyActionHandler actionHandler;
    private final RemediationFilterService filterService;
    private final List<ActionHydrator> hydrators;

    private final AtomicLong lastTriggerTime = new AtomicLong(System.currentTimeMillis());

    /**
     * Primary entry point — called by {@link com.kubiki.palamedes.listener.GraphUpdateListener}
     * on each graph-update message from Metis.
     */
    public void analyze() {
        log.info("AnomalyAgent.analyze() triggered");
        lastTriggerTime.set(System.currentTimeMillis());
        doAnalyze();
    }

    /**
     * Fallback scheduler — runs according to configured rate but only executes the analysis
     * if no event-driven trigger has occurred within the interval.
     */
    @Scheduled(fixedRateString = "${palamedes.engine.fallback-anomaly-scan-rate-ms}")
    public void fallbackAnalyze() {
        long interval = palamedesProperties.engine().fallbackAnomalyScanRateMs();
        long elapsed = System.currentTimeMillis() - lastTriggerTime.get();
        if (elapsed >= interval) {
            log.info("AnomalyAgent: No graph-update received for {}ms — running fallback anomaly scan", elapsed);
            doAnalyze();
        } else {
            log.debug("Fallback scan skipped — last trigger was {}s ago", elapsed / 1000);
        }
    }

    private void doAnalyze() {
        log.info("AnomalyAgent: Starting cluster anomaly scan...");

        List<AnomalyTarget> anomalies = gateway.findAnomalies();
        if (anomalies.isEmpty()) {
            log.info("AnomalyAgent: No anomalies found in GraphDB.");
            return;
        }

        log.info("AnomalyAgent: Discovered {} anomaly targets", anomalies.size());

        Map<IRI, Set<AnomalyTarget>> groupedRemediations = groupAnomaliesByRootCause(anomalies);
        boolean stateChanged = false;

        for (var entry : groupedRemediations.entrySet()) {
            if (processResourceRemediation(entry.getValue())) {
                stateChanged = true;
            }
        }

        finalizeAnalysis(stateChanged);
    }

    private Map<IRI, Set<AnomalyTarget>> groupAnomaliesByRootCause(List<AnomalyTarget> anomalies) {
        Map<IRI, Set<AnomalyTarget>> resourceToIntents = new HashMap<>();
        for (var anomaly : anomalies) {
            AnomalyTarget rootCause = rcaEngine.findRootCause(anomaly);
            resourceToIntents.computeIfAbsent(rootCause.resourceIri(), k -> new HashSet<>()).add(rootCause);
        }
        return resourceToIntents;
    }

    private boolean processResourceRemediation(Set<AnomalyTarget> candidates) {
        AnomalyTarget selected = selectBestRemediation(candidates);

        if (!isIntentAllowed(selected)) {
            return false;
        }

        log.info("AnomalyAgent: Triggering {} for resource {}", 
                selected.intentIri().getLocalName(), selected.resourceName());

        String actionId = utils.generateActionId();
        if (!actionHandler.createActionWorkflow(selected.resourceIri(), selected.intentIri(), actionId)) {
            return false;
        }

        hydrateAndStoreAction(actionId, selected);
        return true;
    }

    private boolean isIntentAllowed(AnomalyTarget target) {
        String intentName = target.intentIri().getLocalName();
        boolean allowed = filterService.isIntentAllowed(intentName);
        
        if (!allowed) {
            log.debug("AnomalyAgent: Intent {} is NOT ALLOWED for resource {}", 
                    intentName, target.resourceName());
        } else {
            log.debug("AnomalyAgent: Intent {} is ALLOWED for resource {}",
                    intentName, target.resourceName());
        }
        
        return allowed;
    }

    private void hydrateAndStoreAction(String actionId, AnomalyTarget target) {
        ActionHydrator hydrator = findBestHydrator(target.intentIri());
        Map<String, String> hydration = hydrator.hydrate(target);
        
        log.info("AnomalyAgent: Storing action hydration for {}: {}", actionId, hydration);
        gateway.storeActionHydration(actionId, hydration);
    }

    private ActionHydrator findBestHydrator(IRI intentIri) {
        return hydrators.stream()
                .filter(h -> h.supports(intentIri))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hydrator found for intent: " + intentIri));
    }

    private AnomalyTarget selectBestRemediation(Set<AnomalyTarget> candidates) {
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // Prefer Workflows over single actions
        return candidates.stream()
                .filter(t -> t.intentIri().getLocalName().endsWith("Workflow"))
                .findFirst()
                .orElse(candidates.iterator().next());
    }

    private void finalizeAnalysis(boolean stateChanged) {
        if (stateChanged) {
            log.info("AnomalyAgent: New remediations planned, waking up MAPE engine.");
            publisher.publishEvent(new EngineWakeupEvent("New anomaly actions created"));
        } else {
            log.info("AnomalyAgent: Analysis completed. No new actions created.");
        }
    }
}
