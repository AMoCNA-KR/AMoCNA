package com.kubiki.palamedes.templating.types;

import org.eclipse.rdf4j.model.IRI;

import static com.kubiki.palamedes.templating.TemplatingConstants.*;

public record IriType(String key, IRI value) implements TemplatingType<IRI> {

    @Override
    public String prefix() {
        return "IRI" + TYPE_INDICATOR;
    }

    @Override
    public String format() {
        return BEGIN_OF_IRI_VARIABLE + value.stringValue() + END_OF_IRI_VARIABLE;
    }
}
