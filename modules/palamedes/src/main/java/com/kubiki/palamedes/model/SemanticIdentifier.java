package com.kubiki.palamedes.model;

import org.eclipse.rdf4j.model.IRI;

/**
 * Domain Object representing a Semantic Identifier (IRI) with business logic.
 * Industrial Rule: Parse at the boundary, don't validate later.
 */
public record SemanticIdentifier(IRI iri) {
    
    public String getFragment() {
        String s = iri.stringValue();
        if (s.contains("#")) return s.substring(s.indexOf("#") + 1);
        if (s.contains("/")) return s.substring(s.lastIndexOf("/") + 1);
        return s;
    }

    public boolean isComplexWorkflow() {
        return getFragment().endsWith("ComplexWorkflow");
    }

    @Override
    public String toString() {
        return iri.toString();
    }
}
