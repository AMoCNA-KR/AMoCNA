#!/bin/bash
set -e
DIR="$(dirname "$0")"

echo "Undeploying GraphDB..."

kubectl delete -f "$DIR/01-storage.yaml" --ignore-not-found
kubectl delete -f "$DIR/00-namespace.yaml" --ignore-not-found

echo "GraphDB cleanup complete."
