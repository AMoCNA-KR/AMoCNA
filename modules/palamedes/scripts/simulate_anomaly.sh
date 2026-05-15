#!/bin/bash
set -e

# Configuration
GRAPHDB_URL=${GRAPHDB_URL:-http://localhost:7200}
REPOSITORY_ID=${GRAPHDB_REPO:-amocna}

# Namespaces (matching application.yml defaults)
MOAM_NS="http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#"
CNEE_NS="http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/"

RESOURCE_NAME=${1:-pod1}
ANOMALY_TYPE=${2:-CPUSaturatedState}

echo "---------------------------------------------------"
echo "Simulating Anomaly for Palamedes"
echo "Resource:  $RESOURCE_NAME"
echo "Anomaly:   $ANOMALY_TYPE"
echo "---------------------------------------------------"

# 1. Check if resource exists or create a mock one
# 2. Inject Anomaly State
# Using SPARQL UPDATE

SPARQL_UPDATE="
PREFIX moam: <$MOAM_NS>
PREFIX cnee: <$CNEE_NS>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

DELETE {
  ?resource cnee:hasCurrentState ?oldState .
  ?oldState rdf:type ?oldType .
}
WHERE {
  ?resource cnee:resourceName \"$RESOURCE_NAME\" .
  OPTIONAL {
    ?resource cnee:hasCurrentState ?oldState .
    ?oldState rdf:type ?oldType .
  }
} ;

INSERT DATA {
  <${CNEE_NS}${RESOURCE_NAME}> rdf:type cnee:CloudNativeEntity ;
                                cnee:resourceName \"$RESOURCE_NAME\" ;
                                cnee:hasCurrentState <${CNEE_NS}${RESOURCE_NAME}_state> .
  <${CNEE_NS}${RESOURCE_NAME}_state> rdf:type cnee:$ANOMALY_TYPE .
}
"

echo "Executing SPARQL Update..."
RESPONSE=$(curl -s -X POST "$GRAPHDB_URL/repositories/$REPOSITORY_ID/statements" \
     -H "Content-Type: application/sparql-update" \
     --data-binary "$SPARQL_UPDATE")

if [ $? -eq 0 ]; then
  echo "SUCCESS: Anomaly injected into GraphDB."
  echo "Palamedes AnomalyAgent should detect it within 5 seconds."
else
  echo "ERROR: Failed to inject anomaly. Is GraphDB up at $GRAPHDB_URL?"
  echo "Response: $RESPONSE"
fi
