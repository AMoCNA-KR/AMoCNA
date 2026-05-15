package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.config.PalamedesProperties;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.springframework.stereotype.Component;

@Component
public class OntologyRegistry {

    private final PalamedesProperties palamedesProperties;
    private final ValueFactory valueFactory = SimpleValueFactory.getInstance();

    public OntologyRegistry(PalamedesProperties palamedesProperties) {
        this.palamedesProperties = palamedesProperties;
    }

    public IRI moam(String fragment) {
        if (fragment != null && fragment.startsWith("http")) {
            return valueFactory.createIRI(fragment);
        }
        return valueFactory.createIRI(palamedesProperties.ontology().moamNamespace(), fragment);
    }

    public IRI cnee(String fragment) {
        if (fragment != null && fragment.startsWith("http")) {
            return valueFactory.createIRI(fragment);
        }
        return valueFactory.createIRI(palamedesProperties.ontology().cneeNamespace(), fragment);
    }

    public IRI bridge(String fragment) {
        if (fragment != null && fragment.startsWith("http")) {
            return valueFactory.createIRI(fragment);
        }
        return valueFactory.createIRI(palamedesProperties.ontology().bridgeNamespace(), fragment);
    }

    public String getMoamNamespace() {
        return palamedesProperties.ontology().moamNamespace();
    }

    public String getCneeNamespace() {
        return palamedesProperties.ontology().cneeNamespace();
    }

    public String getBridgeNamespace() {
        return palamedesProperties.ontology().bridgeNamespace();
    }
}
