#!/bin/bash

# build fresh chain-code and server
sbt clean assembly

mkdir ./target/assembly
cp ./service-node/target/scala-2.12/service-node.jar ./target/assembly/service-node.jar
cp ./service-chain-code/service/target/scala-2.12/chaincode.jar ./target/assembly/service-chaincode.jar

# make a chaincode package:
pushd ./target/assembly/ || exit
  rm -rf ./src
  mkdir ./src
  cp ./service-chaincode.jar ./src/chaincode.jar
  tar -czf service-chain-code.tgz ./src/chaincode.jar
  rm -rf ./src
popd || exit

# bundle admin console:
pushd admin-console || exit
    npm install
    command -v au >>/dev/null 2>/dev/null
    [ $? -eq 1 ] && npm i -g aurelia-cli
    au build --env prod
popd || exit

mkdir ./target/assembly/admin-console
cp ./admin-console/index.html ./target/assembly/admin-console/
cp ./admin-console/favicon.ico ./target/assembly/admin-console/
cp -r ./admin-console/scripts ./target/assembly/admin-console/scripts
