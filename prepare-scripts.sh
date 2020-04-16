#!/bin/bash
FABRIC_SERVICE_NODE_VERSION=$1

rm -rf fabric-service-node-scripts
mkdir fabric-service-node-scripts
cp -r scripts-template/* fabric-service-node-scripts
cat scripts-template/fabric-service-start.sh | sed "s/@FABRIC_SERVICE_NODE_VERSION@/${FABRIC_SERVICE_NODE_VERSION}/g" >./fabric-service-node-scripts/fabric-service-start.sh
tar -cvzf fabric-service-node-scripts.tgz ./fabric-service-node-scripts
