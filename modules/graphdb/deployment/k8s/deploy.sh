#!/bin/bash
set -e
DIR="$(dirname "$0")"
PROJECT_ROOT="$(cd "$DIR/../../../../" && pwd)"

echo "Deploying GraphDB..."

# 00. Create namespace
kubectl apply -f "$DIR/00-namespace.yaml"

# 01. Handle License Secret from .env file
if [ -f "$DIR/.env" ]; then
    echo "Found .env file. Creating/Updating graphdb-license secret..."
    kubectl create secret generic graphdb-license \
        --from-env-file="$DIR/.env" \
        --namespace graphdb \
        --dry-run=client -o yaml | kubectl apply -f -
else
    echo "Warning: No .env file found at $DIR/.env. License application might be skipped."
fi

# 02. Create Ontologies ConfigMap from the root ontology/ folder
echo "Creating graphdb-ontologies ConfigMap from $PROJECT_ROOT/ontology/..."
kubectl create configmap graphdb-ontologies \
    --from-file="$PROJECT_ROOT/ontology/CNEEOnt.rdf" \
    --from-file="$PROJECT_ROOT/ontology/MoaMont.rdf" \
    --from-file="$PROJECT_ROOT/ontology/BridgeOnt.rdf" \
    --namespace graphdb \
    --dry-run=client -o yaml | kubectl apply -f -

# 03-05. Apply remaining resources in order
kubectl apply -f "$DIR/01-storage.yaml"
kubectl apply -f "$DIR/02-init-config.yaml"
kubectl apply -f "$DIR/03-deployment.yaml"
kubectl apply -f "$DIR/04-service.yaml"

echo "GraphDB deployment successful."
