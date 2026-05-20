FROM alpine:latest

RUN apk add --no-cache curl

WORKDIR /provision

COPY provision-graphdb.sh .
COPY *.ttl ./
# In Kustomize context, the ontology folder will be mapped or copied here
# We will copy the ontology directory directly into the image
COPY ontology/ /ontology/

RUN chmod +x provision-graphdb.sh

ENTRYPOINT ["/provision/provision-graphdb.sh"]
