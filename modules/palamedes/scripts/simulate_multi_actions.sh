#!/bin/bash
set -e

# Configuration
GRAPHDB_URL=${GRAPHDB_URL:-http://localhost:7200}
REPOSITORY_ID=${REPOSITORY_ID:-amocna}

# Namespaces
MOAM_NS="http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#"
CNEE_NS="http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/"

echo "---------------------------------------------------"
echo "AMoCNA Multi-Action Simulation Script"
echo "---------------------------------------------------"

simulate_anomaly() {
  local RESOURCE=$1
  local TYPE=$2
  echo ">>> Injecting anomaly '$TYPE' on '$RESOURCE'..."

  SPARQL_UPDATE="
  PREFIX moam: <$MOAM_NS>
  PREFIX cnee: <$CNEE_NS>
  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

  DELETE {
    ?resource cnee:hasState ?oldState .
    ?oldState rdf:type ?oldType .
  }
  WHERE {
    ?resource cnee:resourceName \"$RESOURCE\" .
    OPTIONAL {
      ?resource cnee:hasState ?oldState .
      ?oldState rdf:type ?oldType .
    }
  } ;

  INSERT DATA {
    <${CNEE_NS}${RESOURCE}> rdf:type cnee:CloudNativeEntity ;
                                   cnee:resourceName \"$RESOURCE\" ;
                                   cnee:hasState <${CNEE_NS}${RESOURCE}_state> .
    <${CNEE_NS}${RESOURCE}_state> rdf:type cnee:$TYPE .
  }
  "

  curl -s -f -o /dev/null -X POST "$GRAPHDB_URL/repositories/$REPOSITORY_ID/statements" \
    -H "Content-Type: application/sparql-update" \
    --data-binary "$SPARQL_UPDATE"
}

# 1. Success Scenario: pod1 -> ContainerDead (triggers restart)
simulate_anomaly "pod1" "ContainerDead"

# 2. Sequential Scenario: pod2 -> NeedsMaintenance (triggers multi-step)
simulate_anomaly "pod2" "NeedsMaintenance"

# 3. Fail/Compensate Scenario: pod-fail -> FailingState (triggers restart that fails)
# Note: You can trigger failure in Themis by targeting a non-existent endpoint
# or by manually injecting a FAILURE status into RabbitMQ/GraphDB for simulation.
simulate_anomaly "pod-fail" "ContainerDead"

echo "Done. Use 'observe_workflow.sh' to watch the Petri Net transitions."
