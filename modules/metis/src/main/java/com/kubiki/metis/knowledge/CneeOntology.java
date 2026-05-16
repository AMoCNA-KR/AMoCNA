package com.kubiki.metis.knowledge;

/**
 * Constants for CNEEOnt — local names of all classes, properties, and Kubernetes
 * kinds used across the codebase.
 *
 * <p>Always use these constants instead of string literals to keep the mapping
 * between Kubernetes resources and CNEEOnt vocabulary in one place.
 */
public final class CneeOntology {

    private CneeOntology() {}

    // -------------------------------------------------------------------------
    // Default namespace (overridable via metis.ontology.cneeNamespace).
    // -------------------------------------------------------------------------

    public static final String DEFAULT_NAMESPACE =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    // -------------------------------------------------------------------------
    // Kubernetes kinds — used as IRI prefixes by IriFactory.
    // -------------------------------------------------------------------------

    public static final String KIND_POD     = "Pod";
    public static final String KIND_SERVICE = "Service";
    public static final String KIND_NODE    = "Node";

    // -------------------------------------------------------------------------
    // CNEEOnt classes — local names.
    // -------------------------------------------------------------------------

    public static final String CLASS_EXECUTION_UNIT = "ExecutionUnit";
    public static final String CLASS_SERVICE        = "Service";
    public static final String CLASS_NODE           = "Node";
    public static final String CLASS_METRIC         = "Metric";

    // -------------------------------------------------------------------------
    // Pod phase → CNEEOnt state class local name.
    // -------------------------------------------------------------------------

    public static final String STATE_PENDING   = "ExecutionUnitPending";
    public static final String STATE_RUNNING   = "ExecutionUnitRunning";
    public static final String STATE_FAILED    = "ExecutionUnitFailed";
    public static final String STATE_SUCCEEDED = "ExecutionUnitSucceeded";
    public static final String STATE_UNKNOWN   = "Unknown";

    // -------------------------------------------------------------------------
    // CNEEOnt object properties — local names.
    // -------------------------------------------------------------------------

    public static final String PROP_CONTAINS           = "contains";
    public static final String PROP_IS_PART_OF         = "isPartOf";
    public static final String PROP_HOSTS              = "hosts";
    public static final String PROP_IS_HOSTED_ON       = "isHostedOn";
    public static final String PROP_COMMUNICATES_WITH  = "communicatesWith";
    public static final String PROP_HAS_CURRENT_STATE  = "hasCurrentState";
    public static final String PROP_EMITS_TELEMETRY    = "emitsTelemetry";

    // -------------------------------------------------------------------------
    // CNEEOnt data properties — local names.
    // -------------------------------------------------------------------------

    public static final String PROP_RESOURCE_ID       = "resourceID";
    public static final String PROP_RESOURCE_NAME     = "resourceName";
    public static final String PROP_METRICS_ENDPOINT  = "metricsEndpoint";
}
