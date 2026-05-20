#!/bin/sh
set -e

GRAPHDB_URL=${GRAPHDB_URL:-http://graphdb:7200}
REPOSITORY_ID=${REPOSITORY_ID:-amocna}

echo "---------------------------------------------------"
echo "Waiting for GraphDB to be ready at $GRAPHDB_URL..."
echo "---------------------------------------------------"

until curl -s "$GRAPHDB_URL/protocol" >/dev/null; do
  sleep 2
done
echo "GraphDB is up!"

REPOS_LIST=$(curl -s "$GRAPHDB_URL/rest/repositories")

if echo "$REPOS_LIST" | grep -q "\"id\":\"$REPOSITORY_ID\""; then
  echo "Repository '$REPOSITORY_ID' already exists."
else
  echo "Repository '$REPOSITORY_ID' does not exist. Creating..."

  CONFIG_FILE="/config/amocna-repo-config.ttl"
  if [ ! -f "$CONFIG_FILE" ]; then
    CONFIG_FILE="./amocna-repo-config.ttl"
  fi

  curl -f -X POST "$GRAPHDB_URL/rest/repositories" \
    -H "Content-Type: multipart/form-data" \
    -F "config=@$CONFIG_FILE"

  echo "Repository '$REPOSITORY_ID' created successfully!"
fi

echo "---------------------------------------------------"
echo "Uploading ontologies to $REPOSITORY_ID..."
echo "---------------------------------------------------"

for f in /ontology/*.rdf /ontology/*.ttl; do
  if [ -f "$f" ]; then
    echo "Uploading $f..."

    case "$f" in
    *.rdf) CONTENT_TYPE="application/rdf+xml" ;;
    *.ttl) CONTENT_TYPE="text/turtle" ;;
    *) CONTENT_TYPE="application/octet-stream" ;;
    esac

    STATUS=$(curl -s -f -o /dev/null -w "%{http_code}" -X POST "$GRAPHDB_URL/repositories/$REPOSITORY_ID/statements" \
      -H "Content-Type: $CONTENT_TYPE" \
      --data-binary "@$f")

    if [ "$STATUS" = "204" ] || [ "$STATUS" = "200" ]; then
      echo "SUCCESS: Uploaded $f (Status: $STATUS)"
    else
      echo "ERROR: Failed to upload $f (Status: $STATUS)"
    fi
  fi
done

echo "---------------------------------------------------"
echo "Uploading blueprints..."
echo "---------------------------------------------------"

BLUEPRINT_FILE="/config/restart-action-blueprint.ttl"
if [ ! -f "$BLUEPRINT_FILE" ]; then
  BLUEPRINT_FILE="./restart-action-blueprint.ttl"
fi

if [ -f "$BLUEPRINT_FILE" ]; then
  echo "Uploading RestartAction blueprint from $BLUEPRINT_FILE..."
  curl -s -f -X POST "$GRAPHDB_URL/repositories/$REPOSITORY_ID/statements" \
    -H "Content-Type: text/turtle" \
    --data-binary "@$BLUEPRINT_FILE"
  echo "SUCCESS: RestartAction blueprint uploaded!"
fi

echo "---------------------------------------------------"
echo "GraphDB Provisioning Complete!"
echo "---------------------------------------------------"
