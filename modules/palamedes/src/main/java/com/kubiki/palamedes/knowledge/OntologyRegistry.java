package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.config.PalamedesProperties;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.springframework.stereotype.Component;

@Component
public class OntologyRegistry {

    public static final String HTTP = "http";
    private final PalamedesProperties palamedesProperties;
    private final ValueFactory valueFactory = SimpleValueFactory.getInstance();

    public OntologyRegistry(PalamedesProperties palamedesProperties) {
        this.palamedesProperties = palamedesProperties;
    }

    public IRI actionsOntology(String fragment) {
        if (fragment != null && fragment.startsWith(HTTP)) {
            return valueFactory.createIRI(fragment);
        }
        return valueFactory.createIRI(palamedesProperties.ontology().actionsNamespace(), fragment);
    }

    public IRI resourcesOntology(String fragment) {
        if (fragment != null && fragment.startsWith(HTTP)) {
            return valueFactory.createIRI(fragment);
        }
        return valueFactory.createIRI(palamedesProperties.ontology().resourcesNamespace(), fragment);
    }

    public IRI bridgeOntology(String fragment) {
        if (fragment != null && fragment.startsWith(HTTP)) {
            return valueFactory.createIRI(fragment);
        }
        return valueFactory.createIRI(palamedesProperties.ontology().bridgeNamespace(), fragment);
    }

    public String getCneeNamespace() {
        return palamedesProperties.ontology().resourcesNamespace();
    }
}
