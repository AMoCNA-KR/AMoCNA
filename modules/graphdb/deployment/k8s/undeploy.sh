#!/bin/bash
set -e
DIR="$(dirname "$0")"

echo "Undeploying GraphDB..."

# Delete storage resources (including cluster-scoped PV if defined)
kubectl delete -f "$DIR/01-storage.yaml" --ignore-not-found

# Deleting the namespace removes all associated namespaced resources 
# (Deployments, Services, ConfigMaps, and Secrets).
kubectl delete -f "$DIR/00-namespace.yaml" --ignore-not-found

echo "GraphDB cleanup complete."
