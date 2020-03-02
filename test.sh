#!/bin/bash

#
./clean-docker.sh

./build-image.sh


./bin/fabric-service-generate-static-env.sh ./test

./bin/fabric-service-bootstrap-static-env.sh ./test

#Test messaging
./bin/fabric-service-message-send.sh ./test/org1 org2 hey1
./bin/fabric-service-message-send.sh ./test/org2 org3 hey2
./bin/fabric-service-message-send.sh ./test/org3 org1 hey3
#Checking messages
./bin/fabric-service-messages-list.sh ./test/org1
./bin/fabric-service-messages-list.sh ./test/org2
./bin/fabric-service-messages-list.sh ./test/org3

##Creating contract by ORG1
#
#./bin/fabric-service-contract-create.sh ./test/org1 ./test/org1/contract-invite.json
#
##Checking messages avaliability by mupltyple parties
#./bin/fabric-service-contracts-list.sh ./test/org1
#./bin/fabric-service-contracts-list.sh ./test/org2
#
##Joining to contract by ORG2
#./bin/fabric-service-contract-join.sh ./test/org2 "example" "org1"
