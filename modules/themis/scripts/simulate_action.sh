#!/bin/bash

# Configuration
RABBIT_HOST=${RABBIT_HOST:-localhost}
RABBIT_PORT=${RABBIT_PORT:-15672}
USER=${USER:-guest}
PASS=${PASS:-guest}

# Generate a random ID
ACTION_ID=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "test-action-$(date +%s)")

echo "---------------------------------------------------"
echo "Simulating Action for Themis"
echo "Action ID: $ACTION_ID"
echo "---------------------------------------------------"

# ActionMessage JSON
# We point to Themis's own health check as a test target
PAYLOAD='{
  "actionId": "'$ACTION_ID'",
  "protocol": "REST",
  "instruction": "http://themis:8080/actuator/health",
  "method": "GET",
  "payload": null,
  "data": {},
  "authMechanism": "NONE",
  "timeoutSeconds": 10,
  "isIdempotent": true,
  "maxRetries": 3,
  "expectedStatusCode": 200
}'

# Escape payload for curl
ESCAPED_PAYLOAD=$(echo $PAYLOAD | sed 's/"/\\"/g')

# Publish to RabbitMQ via Management API
curl -s -u $USER:$PASS -X POST http://$RABBIT_HOST:$RABBIT_PORT/api/exchanges/%2f/amocna.direct.exchange/publish \
  -H "content-type:application/json" \
  -d '{
    "properties": {},
    "routing_key": "action",
    "payload": "'"$ESCAPED_PAYLOAD"'",
    "payload_encoding": "string"
  }' | grep -q "routed\":true"

if [ $? -eq 0 ]; then
    echo "SUCCESS: Action published to amocna.action.queue"
else
    echo "ERROR: Failed to publish action. Is RabbitMQ up at $RABBIT_HOST:$RABBIT_PORT?"
fi
