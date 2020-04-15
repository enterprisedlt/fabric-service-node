#!/bin/bash

FABRIC_SERVICE_NODE_VERSION=$1

./build.sh
./prepare-scripts.sh ${FABRIC_SERVICE_NODE_VERSION}

docker build -t enterprisedlt/fabric-service-node:${FABRIC_SERVICE_NODE_VERSION} .
docker tag enterprisedlt/fabric-service-node:${FABRIC_SERVICE_NODE_VERSION} enterprisedlt/fabric-service-node

