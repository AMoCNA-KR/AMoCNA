#!/bin/bash
set -e
DIR="$(dirname "$0")"
PROJECT_ROOT="$(cd "$DIR/../../../../" && pwd)"

echo "=== Deploying GraphDB ==="

# 1. Create namespace
kubectl apply -f "$DIR/00-namespace.yaml"

# 2. Handle License Secret
if [ -f "$DIR/graphdb.license" ]; then
    echo "Creating graphdb-license secret from graphdb.license file..."
    # Decode Base64 to binary and create secret
    base64 -d "$DIR/graphdb.license" > "$DIR/graphdb.license.bin"
    kubectl create secret generic graphdb-license \
        --from-file=GRAPHDB_LICENSE="$DIR/graphdb.license.bin" \
        --namespace graphdb \
        --dry-run=client -o yaml | kubectl apply -f -
    rm "$DIR/graphdb.license.bin"
else
    echo "Warning: No license found. Place graphdb.license in $DIR"
fi

# 3. Create Ontologies ConfigMap
echo "Creating graphdb-ontologies ConfigMap..."
ONTOLOGY_ARGS=""
for f in "$PROJECT_ROOT"/ontology/*.rdf; do
    [ -f "$f" ] && ONTOLOGY_ARGS="$ONTOLOGY_ARGS --from-file=$f"
done
if [ -n "$ONTOLOGY_ARGS" ]; then
    kubectl create configmap graphdb-ontologies \
        $ONTOLOGY_ARGS \
        --namespace graphdb \
        --dry-run=client -o yaml | kubectl apply -f -
else
    echo "Warning: No ontology files found in $PROJECT_ROOT/ontology/"
    kubectl create configmap graphdb-ontologies \
        --namespace graphdb \
        --dry-run=client -o yaml | kubectl apply -f -
fi

# 4. Apply storage (StorageClass + PV + PVC)
kubectl apply -f "$DIR/01-storage.yaml"

# 5. Apply init config, deployment, service
kubectl apply -f "$DIR/02-init-config.yaml"
kubectl apply -f "$DIR/03-deployment.yaml"
kubectl apply -f "$DIR/04-service.yaml"

echo ""
echo "=== Waiting for GraphDB ==="
kubectl rollout status deployment/graphdb -n graphdb --timeout=180s

echo ""
echo "=== GraphDB deployed ==="
echo "  In-cluster: http://graphdb.graphdb.svc.cluster.local:7200"
echo "  Port-forward: kubectl port-forward svc/graphdb 7200:7200 -n graphdb"
