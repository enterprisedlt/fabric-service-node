#!/bin/bash

./clean-docker.sh

gradle clean
gradle service-node:shadowJar
gradle service-chain-code:shadowJar
pushd ./service-chain-code/
 cd ./build/
 mv ./libs ./src
 tar -czf ../chain-code.tgz ./src/chaincode.jar
 rm -rf ./src
popd


# start ORG1
./fabric-service-start.sh ./test/org1
./bootstrap.sh ./test/org1

# make invite from ORG1
./create-invite.sh ./test/org1 ./test/invite_1.json

# join ORG2 using invite
./fabric-service-start.sh ./test/org2
./join.sh ./test/org2 ./test/invite_1.json

## make invite from ORG2
#./create-invite.sh ./test/org2 ./test/invite_2.json
#
## join ORG3 using invite
#./fabric-service-start.sh ./test/org3
#./join.sh ./test/org3 ./test/invite_2.json

## make invite from ORG3
#./create-invite.sh ./test/org3 ./test/invite_3.json
#
## join ORG2 using invite
#./fabric-service-start.sh ./test/org4
#./join.sh ./test/org4 ./test/invite_3.json
