package com.kubiki.palamedes.analyzer;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageRemediationPlannerTest {

    private static final SimpleValueFactory VF = SimpleValueFactory.getInstance();

    @Test
    void parseNamespaceFromDeploymentIri_extractsNamespace() {
        IRI deployment = VF.createIRI(
                "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#Deployment_sock-shop_front-end");
        assertThat(ImageRemediationPlanner.parseNamespaceFromDeploymentIri(deployment))
                .isEqualTo("sock-shop");
    }
}
