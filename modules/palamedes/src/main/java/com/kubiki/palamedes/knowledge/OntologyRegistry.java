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
        return valueFactory.createIRI(palamedesProperties.ontology().moamNamespace(), fragment);
    }

    public String getMoamNamespace() {
        return palamedesProperties.ontology().moamNamespace();
    }
}
