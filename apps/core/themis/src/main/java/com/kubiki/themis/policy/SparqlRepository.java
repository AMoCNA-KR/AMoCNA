package com.kubiki.themis.policy;

import com.kubiki.daedalus.annotation.*;

import java.util.List;

@DaedalusRepository
public interface SparqlRepository {
    @Template(resource = "sparql/fetch-conditions.sparql")
    List<String> fetchConditions(
            @Type(TemplateType.IRI) @Bind("IRI::action") String actionIri,
            @Type(TemplateType.IRI) @Bind("IRI::property") String propertyIri
    );
}
