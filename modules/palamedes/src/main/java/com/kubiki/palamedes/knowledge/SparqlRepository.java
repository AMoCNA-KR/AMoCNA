package com.kubiki.palamedes.knowledge;

import com.kubiki.daedalus.annotation.Bind;
import com.kubiki.daedalus.annotation.DaedalusRepository;
import com.kubiki.daedalus.annotation.Template;

@DaedalusRepository
public interface SparqlRepository {
    @Template(resource = "sparql/find-anomalies.sparql")
    String findAnomalies();

    @Template(resource = "sparql/find-dependents.sparql")
    String findDependents(@Bind("action") String action);

    @Template(resource = "sparql/find-compensation.sparql")
    String findCompensation(@Bind("action") String action);

    @Template(resource = "sparql/find-active-actions.sparql")
    String findActiveActions();
}
