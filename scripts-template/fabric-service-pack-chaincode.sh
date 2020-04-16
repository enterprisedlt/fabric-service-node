#!/bin/bash

pushd $1
 gradle clean shadowJar
 cd ./build/
 mv ./libs ./src
 tar -czf ../$2 ./src/chaincode.jar
 rm -rf ./src
popd