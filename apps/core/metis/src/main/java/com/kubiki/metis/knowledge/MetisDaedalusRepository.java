package com.kubiki.metis.knowledge;

import com.kubiki.daedalus.annotation.*;

@DaedalusRepository
public interface MetisDaedalusRepository {

    @SparqlUpdate(resource = "sparql/insert-entity.sparql")
    void insertEntity(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri,
            @Type(TemplateType.IRI) @Bind("IRI::ontologyType") String ontologyType,
            @Bind("resourceId") String resourceId,
            @Bind("resourceName") String resourceName,
            @Type(TemplateType.PLAIN) @Bind("triples") String triples
    );

    @SparqlUpdate(resource = "sparql/assert-relationship.sparql")
    void assertRelationship(
            @Type(TemplateType.IRI) @Bind("IRI::subjectIri") String subjectIri,
            @Type(TemplateType.PLAIN) @Bind("predicate") String predicate,
            @Type(TemplateType.IRI) @Bind("IRI::objectIri") String objectIri
    );

    @SparqlUpdate(resource = "sparql/assert-relationship-pair.sparql")
    void assertRelationshipPair(
            @Type(TemplateType.IRI) @Bind("IRI::subjectIri") String subjectIri,
            @Type(TemplateType.PLAIN) @Bind("predicate") String predicate,
            @Type(TemplateType.IRI) @Bind("IRI::objectIri") String objectIri,
            @Type(TemplateType.PLAIN) @Bind("inversePredicate") String inversePredicate
    );

    @SparqlUpdate(resource = "sparql/change-state.sparql")
    void changeState(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri,
            @Type(TemplateType.IRI) @Bind("IRI::newStateIri") String newStateIri
    );

    @SparqlUpdate(resource = "sparql/delete-entity.sparql")
    void deleteEntity(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri
    );

    @SparqlUpdate(resource = "sparql/register-metric.sparql")
    void registerMetricMetadata(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri,
            @Bind("endpointUrl") String endpointUrl,
            @Type(TemplateType.IRI) @Bind("IRI::metricIri") String metricIri,
            @Bind("metricName") String metricName
    );
}
