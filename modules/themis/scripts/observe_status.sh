#!/bin/bash

# Configuration
RABBIT_HOST=${RABBIT_HOST:-localhost}
RABBIT_PORT=${RABBIT_PORT:-15672}
USER=${USER:-guest}
PASS=${PASS:-guest}

echo "---------------------------------------------------"
echo "Observing Status Updates from Themis"
echo "Queue: amocna.status.queue"
echo "Press [CTRL+C] to stop"
echo "---------------------------------------------------"

while true; do
  # Get one message from the queue and ACK it
  RESPONSE=$(curl -s -u $USER:$PASS -X POST http://$RABBIT_HOST:$RABBIT_PORT/api/queues/%2f/amocna.status.queue/get \
    -H "content-type:application/json" \
    -d '{"count":1, "ackmode":"ack_requeue_false", "encoding":"auto"}')
  
  # Check if we got a message (it's a JSON array)
  if [[ "$RESPONSE" != "[]" ]]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') - Received Status Update:"
    
    # Try to extract the payload using sed/grep to avoid jq dependency
    PAYLOAD=$(echo $RESPONSE | sed -e 's/.*"payload":"//' -e 's/","payload_encoding".*//' -e 's/\\"/"/g')
    
    echo "$PAYLOAD"
    echo "---------------------------------------------------"
  fi
  
  sleep 1
done
