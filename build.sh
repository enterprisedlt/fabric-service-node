#!/bin/bash
set -e

# build fresh chain-code and server
sbt clean assembly fastOptJS

mkdir ./target/assembly
cp ./service-node/backend/target/scala-2.12/service-node.jar ./target/assembly/service-node.jar

# make a chaincode package:
cp ./service-chain-code/service/target/scala-2.12/chaincode.jar ./target/assembly/service-chaincode.jar
pushd ./target/assembly/
  rm -rf ./src
  mkdir ./src
  cp ./service-chaincode.jar ./src/chaincode.jar
  tar -czf service-chain-code.tgz ./src/chaincode.jar
  rm -rf ./src
popd

# bundle admin console:
mkdir -p ./target/assembly/admin-console
cp -r ./service-node/frontend/bundle/* ./target/assembly/admin-console/
