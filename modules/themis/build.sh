#!/bin/bash
set -e

# Automatically find project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_NAME="themis"
DOCKERFILE_PATH="modules/themis/Dockerfile"

# Default registry if not set
REGISTRY="${REGISTRY:-sglomski}"
PUSH=false

# Parse arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --push) PUSH=true ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
    shift
done

echo "Building $IMAGE_NAME from $PROJECT_ROOT..."
echo "Registry: $REGISTRY, Push: $PUSH"

# Build from project root context
docker build -t "$REGISTRY/$IMAGE_NAME:latest" -f "$PROJECT_ROOT/$DOCKERFILE_PATH" "$PROJECT_ROOT"

if [ "$PUSH" = true ]; then
    echo "Pushing $REGISTRY/$IMAGE_NAME:latest..."
    docker push "$REGISTRY/$IMAGE_NAME:latest"
fi

echo "Success!"
