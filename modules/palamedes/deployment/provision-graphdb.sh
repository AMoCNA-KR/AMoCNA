#!/bin/sh
set -e

GRAPHDB_URL=${GRAPHDB_URL:-http://graphdb:7200}
REPOSITORY_ID=${REPOSITORY_ID:-amocna}

echo "---------------------------------------------------"
echo "Waiting for GraphDB to be ready at $GRAPHDB_URL..."
echo "---------------------------------------------------"

until curl -s "$GRAPHDB_URL/protocol" > /dev/null; do
  sleep 2
done
echo "GraphDB is up!"

# 1. Check if repository exists using the REST API
# We check if the repository ID is present in the list of repositories
REPOS_LIST=$(curl -s "$GRAPHDB_URL/rest/repositories")

if echo "$REPOS_LIST" | grep -q "\"id\":\"$REPOSITORY_ID\""; then
  echo "Repository '$REPOSITORY_ID' already exists."
else
  echo "Repository '$REPOSITORY_ID' does not exist. Creating..."
  
  # Create repository using the Turtle config
  # We use -f to fail on HTTP errors
  curl -f -X POST "$GRAPHDB_URL/rest/repositories" \
       -H "Content-Type: multipart/form-data" \
       -F "config=@/config/amocna-repo-config.ttl"
  
  echo "Repository '$REPOSITORY_ID' created successfully!"
fi

# 2. Upload ontologies
echo "---------------------------------------------------"
echo "Uploading ontologies to $REPOSITORY_ID..."
echo "---------------------------------------------------"

# Support all common extensions
for f in /ontology/*.rdf /ontology/*.owx /ontology/*.owl /ontology/*.ttl; do
  if [ -f "$f" ]; then
    echo "Uploading $f..."
    
    case "$f" in
      *.owx|*.owl|*.rdf) CONTENT_TYPE="application/rdf+xml" ;;
      *.ttl) CONTENT_TYPE="text/turtle" ;;
      *) CONTENT_TYPE="application/octet-stream" ;;
    esac
    
    # Use -f and -w to check status
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

# 3. Upload blueprints
echo "---------------------------------------------------"
echo "Uploading blueprints..."
echo "---------------------------------------------------"

if [ -f "/config/restart-action-blueprint.ttl" ]; then
  echo "Uploading RestartAction blueprint..."
  curl -s -f -X POST "$GRAPHDB_URL/repositories/$REPOSITORY_ID/statements" \
       -H "Content-Type: text/turtle" \
       --data-binary "@/config/restart-action-blueprint.ttl"
  echo "SUCCESS: RestartAction blueprint uploaded!"
fi

echo "---------------------------------------------------"
echo "GraphDB Provisioning Complete!"
echo "---------------------------------------------------"
