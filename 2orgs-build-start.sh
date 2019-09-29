#!/bin/bash

#
./clean-docker.sh

# build fresh chain-code and server
gradle clean
gradle service-node:shadowJar
gradle service-chain-code:shadowJar

pushd admin-console
    npm install
    au build
#     --env prod
popd

# pack chain-code to deploy-able tarball
./bin/fabric-service-pack-chaincode.sh ./service-chain-code service-chain-code.tgz

# start ORG1
./fabric-service-start-dev.sh ./test/org1
#fabric-service-get-user-key.sh ./test/org1 admin admin ./admin-org1.p12

# start ORG2
./fabric-service-start-dev.sh ./test/org2
#fabric-service-get-user-key.sh ./test/org2 admin admin ./admin-org2.p12
