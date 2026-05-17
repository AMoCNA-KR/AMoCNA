package com.kubiki.palamedes.knowledge;

import com.kubiki.daedalus.annotation.Bind;
import com.kubiki.daedalus.annotation.DaedalusRepository;
import com.kubiki.daedalus.annotation.Template;
import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.annotation.Type;

@DaedalusRepository
public interface SparqlRepository {
    @Template(resource = "sparql/find-anomalies.sparql")
    String findAnomalies();

    @Template(resource = "sparql/find-dependents.sparql")
    String findDependents(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @Template(resource = "sparql/find-compensation.sparql")
    String findCompensation(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @Template(resource = "sparql/find-active-actions.sparql")
    String findActiveActions();
}
