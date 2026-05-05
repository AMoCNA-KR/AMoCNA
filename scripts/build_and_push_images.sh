#!/bin/bash
# Exit immediately if a command exits with a non-zero status
set -e

# Use the first argument as the registry name, or default to sglomski
REGISTRY="${1:-sglomski}"

PUSH_IMAGES=${2:-true}

echo "Starting build process for AMoCNA services..."
echo "Target Registry: $REGISTRY"

echo "Building GUI Backend..."
docker build --target gui -t "$REGISTRY/gui:latest" -f Dockerfile.build .

echo "Building Metrics Adapter..."
docker build --target metrics-adapter -t "$REGISTRY/metrics-adapter:latest" -f Dockerfile.build .

echo "Building Business Demo..."
docker build --target business-demo -t "$REGISTRY/business-demo:latest" -f Dockerfile.build .

echo "Building Kubernetes Management..."
docker build --target kubernetes-management -t "$REGISTRY/kubernetes-management:latest" -f Dockerfile.build .

if [ "$PUSH_IMAGES" = true ]; then
    echo "Pushing images to $REGISTRY..."
    docker push "$REGISTRY/gui:latest"
    docker push "$REGISTRY/metrics-adapter:latest"
    docker push "$REGISTRY/business-demo:latest"
    docker push "$REGISTRY/kubernetes-management:latest"
    echo "Success! All images are built and pushed."
else
    echo "Success! All images are built locally."
fi
