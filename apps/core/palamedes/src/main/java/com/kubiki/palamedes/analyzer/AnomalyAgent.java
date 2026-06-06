package com.kubiki.palamedes.analyzer;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.common.vulnerability.UpgradePolicy;
import com.kubiki.common.vulnerability.VulnerabilityCatalog;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.AnomalyTarget;
import com.kubiki.palamedes.model.ImageUpdateTarget;
import com.kubiki.palamedes.pipeline.EngineWakeupEvent;
import com.kubiki.palamedes.reasoner.RcaEngine;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AnomalyAgent (MAPE-Analyze):
 * Scans for resources in AnomalyState and creates remediation workflows.
 *
 * <h2>Triggering strategy</h2>
 * <ul>
 *   <li><b>Event-driven:</b> {@link #analyze()} is called by
 *       {@link com.kubiki.palamedes.listener.GraphUpdateListener} immediately
 *       when a graph-update message arrives from Metis via RabbitMQ.</li>
 *   <li><b>Fallback poll:</b> If no graph-update message has been received for
 *       10 minutes, the scheduled {@link #fallbackAnalyze()} fires to catch
 *       any missed events (e.g. after a RabbitMQ outage).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AnomalyAgent {
    private static final Logger log = LoggerFactory.getLogger(AnomalyAgent.class);
    private static final String IMAGE_UPDATE_INTENT = "ImageUpdateIntent";

    private final GraphDBGateway gateway;
    private final RcaEngine rcaEngine;
    private final ActionUtils utils;
    private final ApplicationEventPublisher publisher;
    private final PalamedesProperties palamedesProperties;
    private final OntologyRegistry ontologyRegistry;
    private final VulnerabilityCatalog vulnerabilityCatalog;
    private final AnomalyActionHandler actionHandler;

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
     * This catches anomalies that might have been missed due to messaging outages.
     */
    @Scheduled(fixedRateString = "${palamedes.engine.fallback-anomaly-scan-rate-ms}")
    public void fallbackAnalyze() {
        long interval = palamedesProperties.engine().fallbackAnomalyScanRateMs();
        long elapsed = System.currentTimeMillis() - lastTriggerTime.get();
        if (elapsed >= interval) {
            log.info("AnomalyAgent: No graph-update received for {}ms — running fallback anomaly scan",
                    elapsed);
            doAnalyze();
        } else {
            log.debug("Fallback poll skipped — last trigger was {}s ago", elapsed / 1000);
        }
    }

    private void doAnalyze() {
        log.info("AnomalyAgent: doAnalyze() starting scan for cluster anomalies...");

        List<AnomalyTarget> anomalies = gateway.findAnomalies();
        log.info("AnomalyAgent: Found {} anomaly targets in GraphDB", anomalies.size());
        boolean stateChanged = false;

        IRI imageUpdateIntentIri = ontologyRegistry.actionsOntology(IMAGE_UPDATE_INTENT);
        UpgradePolicy upgradePolicy = UpgradePolicy.valueOf(
                palamedesProperties.vulnerability().upgradePolicy().toUpperCase());

        // 1. Group anomalies by their root cause resource to avoid redundant or conflicting remediations
        Map<IRI, Set<AnomalyTarget>> resourceToIntents = new HashMap<>();
        for (var anomaly : anomalies) {
            AnomalyTarget rootCause = rcaEngine.findRootCause(anomaly);
            resourceToIntents.computeIfAbsent(rootCause.resourceIri(), k -> new HashSet<>()).add(rootCause);
        }

        for (Map.Entry<IRI, Set<AnomalyTarget>> entry : resourceToIntents.entrySet()) {
            Set<AnomalyTarget> candidates = entry.getValue();

            // 2. Optimization: Select the most comprehensive remediation (Workflow > Simple Action)
            AnomalyTarget selected = selectBestRemediation(candidates);
            log.info("AnomalyAgent: Selected remediation {} for resource {} from {} candidates", 
                    selected.intentIri().getLocalName(), selected.resourceName(), candidates.size());

            String actionId = utils.generateActionId();
            if (!actionHandler.createActionWorkflow(selected.resourceIri(), selected.intentIri(), actionId)) {
                continue;
            }

            // Hydrate parameters for execution
            Map<String, String> hydration = new HashMap<>();
            hydration.put("namespace", ImageRemediationPlanner.parseNamespaceFromDeploymentIri(selected.resourceIri()));
            hydration.put("resourceName", selected.resourceName());

            // Specialized hydration for ImageUpdateIntent
            if (selected.intentIri().equals(imageUpdateIntentIri)) {
                Optional<ImageUpdateTarget> details = gateway.findWorkloadDetails(selected.resourceIri());
                details.ifPresent(d -> {
                    hydration.put("containerName", d.containerName());
                    hydration.put("imageRepository", d.imageRepository());
                    log.info("AnomalyAgent: Hydrating details for container {} in repository {}", 
                            d.containerName(), d.imageRepository());

                    vulnerabilityCatalog.selectFixVersion(d.imageRepository(), d.currentVersion(), upgradePolicy)
                            .ifPresent(v -> {
                                hydration.put("targetVersion", v);
                                log.info("AnomalyAgent: Selected fix version {} for repository {} (current version: {})", 
                                        v, d.imageRepository(), d.currentVersion());
                            });
                });
            }

            log.info("AnomalyAgent: Storing action hydration for actionId {}: {}", actionId, hydration);
            gateway.storeActionHydration(actionId, hydration);
            stateChanged = true;
        }

        if (stateChanged) {
            log.info("AnomalyAgent: State changed, publishing EngineWakeupEvent");
            publisher.publishEvent(new EngineWakeupEvent("New anomaly actions created"));
        } else {
            log.info("AnomalyAgent: doAnalyze() completed. No state changes.");
        }
    }

    /**
     * Heuristic to select the best remediation intent among multiple candidates for the same resource.
     * Prioritizes Workflows over individual Intents.
     */
    private AnomalyTarget selectBestRemediation(Set<AnomalyTarget> candidates) {
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // Prefer Workflows
        return candidates.stream()
                .filter(t -> t.intentIri().getLocalName().endsWith("Workflow"))
                .findFirst()
                .orElse(candidates.iterator().next());
    }
}
