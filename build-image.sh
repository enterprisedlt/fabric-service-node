#!/bin/bash
set -e

FABRIC_SERVICE_NODE_VERSION=$1
if [[ -n $FABRIC_SERVICE_NODE_VERSION ]]; then
./build.sh
./prepare-scripts.sh ${FABRIC_SERVICE_NODE_VERSION}

docker build -t enterprisedlt/fabric-service-node:${FABRIC_SERVICE_NODE_VERSION} .
docker tag enterprisedlt/fabric-service-node:${FABRIC_SERVICE_NODE_VERSION} enterprisedlt/fabric-service-node
else
  echo "Please provide fabric service node version as first opt"
fi