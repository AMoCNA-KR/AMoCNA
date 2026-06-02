package com.kubiki.palamedes.analyzer;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.common.vulnerability.UpgradePolicy;
import com.kubiki.common.vulnerability.VulnerabilityCatalog;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ImageInTopology;
import com.kubiki.palamedes.model.ImageUpdateTarget;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ImageRemediationPlanner {

    private static final Logger log = LoggerFactory.getLogger(ImageRemediationPlanner.class);
    private static final String IMAGE_UPDATE_INTENT = "ImageUpdateIntent";

    private final GraphDBGateway gateway;
    private final ActionUtils utils;
    private final VulnerabilityCatalog vulnerabilityCatalog;
    private final PalamedesProperties properties;
    private final OntologyRegistry ontologyRegistry;

    /**
     * Matches sensed {@code Image} entities in GraphDB against the CVE catalog and plans
     * {@code ImageUpdateIntent} workflows for affected deployments. No anomaly state is written to GraphDB.
     */
    public boolean scanCatalogAndPlan() {
        log.info("ImageRemediationPlanner: Starting image vulnerability scan against CVE catalog...");
        UpgradePolicy upgradePolicy = UpgradePolicy.valueOf(
                properties.vulnerability().upgradePolicy().toUpperCase());
        IRI intentIri = ontologyRegistry.actionsOntology(IMAGE_UPDATE_INTENT);

        List<ImageInTopology> images = gateway.findImagesInTopology();
        log.info("ImageRemediationPlanner: Found {} images in current topology to evaluate", images.size());

        boolean plannedAny = false;
        int plannedCount = 0;
        for (ImageInTopology image : images) {
            var cves = vulnerabilityCatalog.lookup(image.imageRepository(), image.version());
            if (cves.isEmpty()) {
                log.debug("ImageRemediationPlanner: Image {}:{} is clean (no matching CVEs)", image.imageRepository(), image.version());
                continue;
            }

            log.warn("ImageRemediationPlanner: Detected vulnerable image {}:{} with {} active CVEs in catalog!", 
                    image.imageRepository(), image.version(), cves.size());

            if (planForImage(image.imageIri(), intentIri, upgradePolicy)) {
                plannedAny = true;
                plannedCount++;
            }
        }
        log.info("ImageRemediationPlanner: Completed vulnerability scan. Remediated {} vulnerable images.", plannedCount);
        return plannedAny;
    }

    private boolean planForImage(IRI imageIri, IRI intentIri, UpgradePolicy upgradePolicy) {
        List<ImageUpdateTarget> targets = gateway.findWorkloadsByVulnerableImage(imageIri);
        if (targets.isEmpty()) {
            log.info("ImageRemediationPlanner: No active workloads running vulnerable image {}", imageIri);
            return false;
        }

        Map<IRI, ImageUpdateTarget> uniqueByDeployment = new LinkedHashMap<>();
        for (ImageUpdateTarget target : targets) {
            Optional<String> fixVersion = vulnerabilityCatalog.selectFixVersion(
                    target.imageRepository(), target.currentVersion(), upgradePolicy);
            if (fixVersion.isEmpty()) {
                log.warn("Catalog match for {}:{} but no fix under policy {}",
                        target.imageRepository(), target.currentVersion(), upgradePolicy);
                continue;
            }
            ImageUpdateTarget resolved = new ImageUpdateTarget(
                    target.deploymentIri(),
                    target.deploymentName(),
                    target.namespace(),
                    target.containerName(),
                    target.imageRepository(),
                    target.currentVersion(),
                    fixVersion.get(),
                    target.serviceIri(),
                    target.serviceName());
            uniqueByDeployment.putIfAbsent(resolved.deploymentIri(), resolved);
        }

        if (uniqueByDeployment.isEmpty()) {
            log.info("ImageRemediationPlanner: No valid upgrade path found for workloads using image {}", imageIri);
            return false;
        }

        for (ImageUpdateTarget target : uniqueByDeployment.values()) {
            String actionId = utils.generateActionId();
            log.info("ImageRemediationPlanner: Planning image update action {} for deployment {}/{} (current: {}:{}, target: {})",
                    actionId, target.namespace(), target.deploymentName(),
                    target.imageRepository(), target.currentVersion(), target.targetVersion());
            gateway.createActionWorkflow(target.deploymentIri(), intentIri, actionId);
            gateway.storeActionHydration(actionId, Map.of(
                    "containerName", target.containerName(),
                    "imageRepository", target.imageRepository(),
                    "targetVersion", target.targetVersion(),
                    "namespace", target.namespace()));
            log.info("Planned image update for deployment {}/{} (service: {}) -> {}:{}",
                    target.namespace(), target.deploymentName(),
                    target.serviceName() != null ? target.serviceName() : "n/a",
                    target.imageRepository(), target.targetVersion());
        }
        return true;
    }

    public static String parseNamespaceFromDeploymentIri(IRI deploymentIri) {
        String local = URLDecoder.decode(deploymentIri.getLocalName(), StandardCharsets.UTF_8);
        int kindSep = local.indexOf('_');
        if (kindSep < 0) {
            return "default";
        }
        String rest = local.substring(kindSep + 1);
        int nsSep = rest.indexOf('_');
        if (nsSep < 0) {
            return "default";
        }
        return rest.substring(0, nsSep);
    }
}
