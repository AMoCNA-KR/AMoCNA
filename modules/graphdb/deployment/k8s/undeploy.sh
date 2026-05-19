#!/bin/bash
set -e

echo "Undeploying GraphDB..."

# Delete namespace first (cascades to deployment, PVC, services, configmaps)
kubectl delete namespace graphdb --ignore-not-found --timeout=60s

# Delete cluster-scoped resources (PV, StorageClass)
kubectl delete pv graphdb-pv --ignore-not-found
kubectl delete storageclass local-storage --ignore-not-found

echo "GraphDB cleanup complete."
