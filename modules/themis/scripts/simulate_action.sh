#!/bin/bash

# Configuration
RABBIT_HOST=${RABBIT_HOST:-localhost}
RABBIT_PORT=${RABBIT_PORT:-15672}
RABBIT_USER=${RABBIT_USER:-guest}
RABBIT_PASS=${RABBIT_PASS:-guest}

PROTOCOL=${1:-REST}
EXPECTED_STATUS=${2:-200}

# Generate a random ID
ACTION_ID=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "test-action-$(date +%s)")

echo "---------------------------------------------------"
echo "Simulating Action for Themis"
echo "Action ID: $ACTION_ID"
echo "Protocol:  $PROTOCOL"
echo "Expected:  $EXPECTED_STATUS"
echo "---------------------------------------------------"

if [ "$PROTOCOL" == "SHELL" ]; then
  # Shell Example
  PAYLOAD='{
    "actionId": "'$ACTION_ID'",
    "protocol": "SHELL",
    "instruction": "echo \"Hello Themis\"; exit '$EXPECTED_STATUS'",
    "method": null,
    "payload": null,
    "data": {"name": "Themis"},
    "authMechanism": "NONE",
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
    "instruction": "http://themis:8080/actuator/health",
    "method": "GET",
    "payload": null,
    "data": {},
    "authMechanism": "NONE",
    "timeoutSeconds": 10,
    "isIdempotent": true,
    "maxRetries": 3,
    "expectedStatusCode": '$EXPECTED_STATUS'
  }'
fi

ESCAPED_PAYLOAD=$(printf '%s' "$PAYLOAD" | jq -Rs .)

curl -s -u "$RABBIT_USER:$RABBIT_PASS" -X POST "http://$RABBIT_HOST:$RABBIT_PORT/api/exchanges/%2f/amocna.direct.exchange/publish" \
  -H "content-type:application/json" \
  -d "$(cat <<EOF
{
  "properties": {},
  "routing_key": "action",
  "payload": ${ESCAPED_PAYLOAD},
  "payload_encoding": "string"
}
EOF
)" | grep -q "routed\":true"

if [ $? -eq 0 ]; then
  echo "SUCCESS: Action published to amocna.action.queue"
else
  echo "ERROR: Failed to publish action. Is RabbitMQ up at $RABBIT_HOST:$RABBIT_PORT?"
fi
