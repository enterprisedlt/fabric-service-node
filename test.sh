#!/bin/bash

# before running this script do:
# export PATH=$PATH:<path to fabric-service-node/bin>

./clean-docker.sh

# build fresh chain-code and server
gradle clean
# gradle service-node:shadowJar
gradle service-chain-code:shadowJar
gradle services:identity-service:shadowJar
gradle services:process-management-service:shadowJar
gradle services:administration-service:shadowJar

# pack chain-code to deploy-able tarball
fabric-service-pack-chaincode.sh ./service-chain-code service-chain-code.tgz


# start ORG1
./fabric-service-start-dev.sh ./test/org1
#fabric-service-bootstrap.sh ./test/org1 ./test/org1/bootstrap.json
#
#
##fabric-service-create-user.sh ./test/org1 abc
##fabric-service-get-user-key.sh ./test/org1 abc abc ./abc.p12
#
#
## make invite from ORG1
#fabric-service-create-invite.sh ./test/org1 ./test/invite_1.json
#
## join ORG2 using invite
#./fabric-service-start-dev.sh ./test/org2
#fabric-service-join.sh ./test/org2 ./test/invite_1.json

## make invite from ORG2
#fabric-service-create-invite.sh ./test/org2 ./test/invite_2.json
#
## join ORG3 using invite
#./fabric-service-start-dev.sh ./test/org3
#fabric-service-join.sh ./test/org3 ./test/invite_2.json
#
### make invite from ORG3
#fabric-service-create-invite.sh ./test/org3 ./test/invite_3.json
##
### join ORG2 using invite
#./fabric-service-start-dev.sh ./test/org4
#fabric-service-join.sh ./test/org4 ./test/invite_3.json
#
##Test messaging
#fabric-service-message-send.sh ./test/org1 org2 hey1
#fabric-service-message-send.sh ./test/org2 org3 hey2
#fabric-service-message-send.sh ./test/org3 org1 hey3
##Checking messages
#fabric-service-messages-list.sh ./test/org1
#fabric-service-messages-list.sh ./test/org2
#fabric-service-messages-list.sh ./test/org3
#
##Creating contract by ORG1
#
#fabric-service-contract-create.sh ./test/org1 ./test/org1/contract-invite.json
#
##Checking messages avaliability by mupltyple parties
#fabric-service-contracts-list.sh ./test/org1
#fabric-service-contracts-list.sh ./test/org2
#
##Joining to contract by ORG2
#fabric-service-contract-join.sh ./test/org2 "example" "org1"
