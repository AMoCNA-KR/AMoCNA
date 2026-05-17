#!/bin/bash
set -e
kubectl delete namespace metis --ignore-not-found
kubectl delete clusterrole metis-sensor --ignore-not-found
kubectl delete clusterrolebinding metis-sensor --ignore-not-found
echo "Metis removed."
