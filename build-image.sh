#!/bin/sh

# build fresh chain-code and server
gradle clean
gradle service-node:shadowJar
gradle service-chain-code:shadowJar

# pack chain-code to deploy-able tarball
./bin/fabric-service-pack-chaincode.sh ./service-chain-code service-chain-code.tgz

docker build -t enterprisedlt/fabric-service-node:1.4.2-rc-1 .
docker tag enterprisedlt/fabric-service-node:1.4.2-rc-1 enterprisedlt/fabric-service-node
