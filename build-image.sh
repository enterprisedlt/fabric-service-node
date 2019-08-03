#!/bin/sh

# build fresh chain-code and server
gradle clean
gradle service-node:shadowJar
gradle service-chain-code:shadowJar

# pack chain-code to deploy-able tarball
./pack-chaincode.sh ./service-chain-code service-chain-code.tgz

docker build -t enterprisedlt/fabric-service-node:1.4.1 .
docker tag enterprisedlt/fabric-service-node:1.4.1 enterprisedlt/fabric-service-node
