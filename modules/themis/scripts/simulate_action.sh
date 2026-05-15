#!/bin/bash

# Configuration
RABBIT_HOST=${RABBIT_HOST:-localhost}
RABBIT_PORT=${RABBIT_PORT:-15672}
RABBIT_USER=${RABBIT_USER:-guest}
RABBIT_PASS=${RABBIT_PASS:-guest}

PROTOCOL=${1:-REST}
EXPECTED_STATUS=${2:-200}
AUTH_MECHANISM=${3:-NONE}

# Generate a random ID
ACTION_ID=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "test-action-$(date +%s)")

echo "---------------------------------------------------"
echo "Simulating Action for Themis"
echo "Action ID: $ACTION_ID"
echo "Protocol:  $PROTOCOL"
echo "Expected:  $EXPECTED_STATUS"
echo "Auth:      $AUTH_MECHANISM"
echo "---------------------------------------------------"

if [ "$PROTOCOL" == "SHELL" ]; then
  # Shell Example
  PAYLOAD='{
    "actionId": "'$ACTION_ID'",
    "protocol": "SHELL",
    "instruction": "echo \"Hello Themis - ActionID: '$ACTION_ID'\"; exit '$EXPECTED_STATUS'",
    "method": null,
    "payload": null,
    "authMechanism": "'$AUTH_MECHANISM'",
    "timeoutSeconds": 10,
    "isIdempotent": true,
    "maxRetries": 3,
    "expectedStatusCode": '$EXPECTED_STATUS'
  }'
else
  # REST Example
  PAYLOAD='{
    "actionId": "'$ACTION_ID'",
    "protocol": "REST",
    "instruction": "http://localhost:8080/actuator/health",
    "method": "GET",
    "payload": null,
    "authMechanism": "'$AUTH_MECHANISM'",
    "timeoutSeconds": 10,
    "isIdempotent": true,
    "maxRetries": 3,
    "expectedStatusCode": '$EXPECTED_STATUS'
  }'
fi

# Use jq to format payload correctly
ESCAPED_PAYLOAD=$(echo "$PAYLOAD" | jq -c .)

# Publish to RabbitMQ using Management API
curl -s -u "$RABBIT_USER:$RABBIT_PASS" -X POST "http://$RABBIT_HOST:$RABBIT_PORT/api/exchanges/%2f/amocna.direct.exchange/publish" \
  -H "content-type:application/json" \
  -d '{
  "properties": {"content_type":"application/json"},
  "routing_key": "action",
  "payload": "'$(echo "$ESCAPED_PAYLOAD" | sed 's/"/\\"/g')'",
  "payload_encoding": "string"
}' | grep -q "routed\":true"

if [ $? -eq 0 ]; then
  echo "SUCCESS: Action published to amocna.action.queue"
  echo "Payload: $ESCAPED_PAYLOAD"
else
  echo "ERROR: Failed to publish action. Is RabbitMQ up at $RABBIT_HOST:$RABBIT_PORT?"
fi
