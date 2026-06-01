package com.kubiki.palamedes.analyzer;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.RegistryAuthTarget;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plans {@code AddImagePullSecretIntent} workflows for workloads stuck in
 * {@code ImagePullBackOff} because they lack an {@code imagePullSecret}.
 *
 * <p>The correct secret is inferred relationally from the graph: a sibling
 * workload in the same namespace already pulls from the same registry with a
 * working secret (see {@code find-registry-auth-failures.sparql}). If no such
 * sibling exists (e.g. an image typo), nothing is planned — that restraint is
 * why no BridgeOnt 1:1 mapping is used for this anomaly.
 */
@Service
@RequiredArgsConstructor
public class RegistryCredentialPlanner {

    private static final Logger log = LoggerFactory.getLogger(RegistryCredentialPlanner.class);
    private static final String ADD_IMAGE_PULL_SECRET_INTENT = "AddImagePullSecretIntent";

    private final GraphDBGateway gateway;
    private final ActionUtils utils;
    private final OntologyRegistry ontologyRegistry;

    /**
     * Scans GraphDB for registry-auth failures and plans a patch for each affected
     * Deployment.
     *
     * @return {@code true} if at least one workflow was planned
     */
    public boolean scanAndPlan() {
        Map<IRI, RegistryAuthTarget> uniqueByDeployment = new LinkedHashMap<>();
        for (RegistryAuthTarget target : gateway.findRegistryAuthFailures()) {
            uniqueByDeployment.putIfAbsent(target.deploymentIri(), target);
        }
        if (uniqueByDeployment.isEmpty()) {
            return false;
        }

        IRI intentIri = ontologyRegistry.actionsOntology(ADD_IMAGE_PULL_SECRET_INTENT);
        for (RegistryAuthTarget target : uniqueByDeployment.values()) {
            String actionId = utils.generateActionId();
            gateway.createActionWorkflow(target.deploymentIri(), intentIri, actionId);
            gateway.storeActionHydration(actionId, Map.of(
                    "namespace", target.namespace(),
                    "pullSecretName", target.pullSecretName()));
            log.info("Planned imagePullSecret patch for deployment {}/{} -> secret {}",
                    target.namespace(), target.deploymentName(), target.pullSecretName());
        }
        return true;
    }
}
