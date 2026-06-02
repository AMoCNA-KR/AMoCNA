package com.kubiki.palamedes.analyzer;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.common.vulnerability.UpgradePolicy;
import com.kubiki.common.vulnerability.VulnerabilityCatalog;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.AnomalyTarget;
import com.kubiki.palamedes.model.ImageUpdateTarget;
import com.kubiki.palamedes.pipeline.EngineWakeupEvent;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final com.kubiki.palamedes.reasoner.RcaEngine rcaEngine;
    private final ActionUtils utils;
    private final ApplicationEventPublisher publisher;
    private final PalamedesProperties palamedesProperties;
    private final OntologyRegistry ontologyRegistry;
    private final VulnerabilityCatalog vulnerabilityCatalog;

    private final AtomicLong lastTriggerTime = new AtomicLong(System.currentTimeMillis());

    /**
     * Primary entry point — called by {@link com.kubiki.palamedes.listener.GraphUpdateListener}
     * on each graph-update message from Metis.
     */
    public void analyze() {
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
            log.info("No graph-update received for {}ms — running fallback anomaly scan",
                    elapsed);
            doAnalyze();
        } else {
            log.debug("Fallback poll skipped — last trigger was {}s ago", elapsed / 1000);
        }
    }

    private void doAnalyze() {
        log.debug("Scanning for cluster anomalies...");

        List<AnomalyTarget> anomalies = gateway.findAnomalies();
        boolean stateChanged = false;

        IRI imageUpdateIntentIri = ontologyRegistry.actionsOntology(IMAGE_UPDATE_INTENT);
        UpgradePolicy upgradePolicy = UpgradePolicy.valueOf(
                palamedesProperties.vulnerability().upgradePolicy().toUpperCase());

        for (var anomaly : anomalies) {
            log.info("Anomaly detected: resource {} needs {}", anomaly.resourceName(), anomaly.intentIri());

            AnomalyTarget rootCause = rcaEngine.findRootCause(anomaly);
            if (!rootCause.resourceIri().equals(anomaly.resourceIri())) {
                log.info("Root cause analysis redirected {} -> {} (intent: {})",
                        anomaly.resourceName(), rootCause.resourceName(), rootCause.intentIri());
            }

            String actionId = utils.generateActionId();

            gateway.createActionWorkflow(rootCause.resourceIri(), rootCause.intentIri(), actionId);

            // Hydrate parameters for execution
            Map<String, String> hydration = new HashMap<>();
            hydration.put("namespace", ImageRemediationPlanner.parseNamespaceFromDeploymentIri(rootCause.resourceIri()));
            hydration.put("resourceName", rootCause.resourceName());

            // Specialized hydration for ImageUpdateIntent
            if (rootCause.intentIri().equals(imageUpdateIntentIri)) {
                Optional<ImageUpdateTarget> details = gateway.findWorkloadDetails(rootCause.resourceIri());
                details.ifPresent(d -> {
                    hydration.put("containerName", d.containerName());
                    hydration.put("imageRepository", d.imageRepository());

                    vulnerabilityCatalog.selectFixVersion(d.imageRepository(), d.currentVersion(), upgradePolicy)
                            .ifPresent(v -> hydration.put("targetVersion", v));
                });
            }

            gateway.storeActionHydration(actionId, hydration);
            stateChanged = true;
        }

        if (stateChanged) {
            publisher.publishEvent(new EngineWakeupEvent("New anomaly actions created"));
        }
    }
}
