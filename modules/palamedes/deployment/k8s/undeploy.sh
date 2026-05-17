#!/bin/bash
set -e
kubectl delete namespace palamedes --ignore-not-found
echo "Palamedes removed."
