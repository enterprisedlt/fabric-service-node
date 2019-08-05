#!/bin/bash

./clean-docker.sh

# build fresh chain-code and server
gradle clean
gradle service-node:shadowJar
gradle service-chain-code:shadowJar

# pack chain-code to deploy-able tarball
./pack-chaincode.sh ./service-chain-code service-chain-code.tgz


# start ORG1
./fabric-service-start-dev.sh ./test/org1
./bootstrap.sh ./test/org1

#./create-user.sh ./test/org1 abc
#./get-user-key.sh ./test/org1 abc abc ./abc.p12

# make invite from ORG1
./create-invite.sh ./test/org1 ./test/invite_1.json

# join ORG2 using invite
./fabric-service-start-dev.sh ./test/org2
./join.sh ./test/org2 ./test/invite_1.json

# make invite from ORG2
./create-invite.sh ./test/org2 ./test/invite_2.json

# join ORG3 using invite
./fabric-service-start-dev.sh ./test/org3
./join.sh ./test/org3 ./test/invite_2.json

# make invite from ORG3
./create-invite.sh ./test/org3 ./test/invite_3.json

# join ORG2 using invite
./fabric-service-start-dev.sh ./test/org4
./join.sh ./test/org4 ./test/invite_3.json
