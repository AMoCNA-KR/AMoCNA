package com.kubiki.metis.knowledge;

import com.kubiki.metis.config.MetisProperties;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.springframework.stereotype.Component;

@Component
public class OntologyRegistry {

    private final MetisProperties metisProperties;
    private final ValueFactory valueFactory = SimpleValueFactory.getInstance();

    public OntologyRegistry(MetisProperties metisProperties) {
        this.metisProperties = metisProperties;
    }

    public IRI cnee(String fragment) {
        return valueFactory.createIRI(metisProperties.ontology().cneeNamespace(), fragment);
    }

    public String getCneeNamespace() {
        return metisProperties.ontology().cneeNamespace();
    }
}
