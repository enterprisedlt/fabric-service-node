#!/bin/bash

# build fresh chain-code and server
sbt clean assembly

pushd admin-console || exit
    npm install
    au build --env prod
popd || exit

# pack chain-code to deploy-able tarball
pushd ./service-chain-code/service/target/ || exit
  mkdir src
  cp ./scala-2.12/chaincode.jar ./src/chaincode.jar
  tar -czf service-chain-code.tgz ./src/chaincode.jar
  rm -rf src
popd || exit

#./bin/fabric-service-pack-chaincode.sh ./service-chain-code service-chain-code.tgz

docker build -t enterprisedlt/fabric-service-node:1.4.2-rc-3 .
docker tag enterprisedlt/fabric-service-node:1.4.2-rc-3 enterprisedlt/fabric-service-node
