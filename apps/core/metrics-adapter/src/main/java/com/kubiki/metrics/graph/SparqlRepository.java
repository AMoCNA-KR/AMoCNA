package com.kubiki.metrics.graph;

import com.kubiki.daedalus.annotation.Bind;
import com.kubiki.daedalus.annotation.DaedalusRepository;
import com.kubiki.daedalus.annotation.Template;
import com.kubiki.daedalus.annotation.Type;
import com.kubiki.daedalus.core.format.TemplateType;

@DaedalusRepository
public interface SparqlRepository {
    @Template(resource = "sparql/instantiate-anomaly.sparql")
    String instantiateAnomaly(
            @Type(TemplateType.IRI) @Bind("IRI::targetResource") String targetResource,
            @Type(TemplateType.IRI) @Bind("IRI::anomaly") String anomaly,
            @Type(TemplateType.IRI) @Bind("IRI::anomalyType") String anomalyType,
            @Bind("LITERAL::timestamp") String timestamp
    );
}
