#!/bin/bash

# before running this script do:
# export PATH=$PATH:<path to fabric-service-node/bin>

./clean-docker.sh

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

# start ORG1
./fabric-service-start-dev.sh ./test/org1
./bin/fabric-service-bootstrap.sh ./test/org1 ./test/org1/bootstrap.json


#fabric-service-create-user.sh ./test/org1 abc
#fabric-service-get-user-key.sh ./test/org1 abc abc ./abc.p12


# make invite from ORG1
./bin/fabric-service-create-invite.sh ./test/org1 ./test/invite_1.json

# join ORG2 using invite
./fabric-service-start-dev.sh ./test/org2
./bin/fabric-service-join.sh ./test/org2 ./test/invite_1.json

# make invite from ORG2
./bin/fabric-service-create-invite.sh ./test/org2 ./test/invite_2.json

# join ORG3 using invite
./fabric-service-start-dev.sh ./test/org3
./bin/fabric-service-join.sh ./test/org3 ./test/invite_2.json

## make invite from ORG3
./bin/fabric-service-create-invite.sh ./test/org3 ./test/invite_3.json
#
## join ORG2 using invite
./fabric-service-start-dev.sh ./test/org4
./bin/fabric-service-join.sh ./test/org4 ./test/invite_3.json

#Test messaging
./bin/fabric-service-message-send.sh ./test/org1 org2 hey1
./bin/fabric-service-message-send.sh ./test/org2 org3 hey2
./bin/fabric-service-message-send.sh ./test/org3 org1 hey3
#Checking messages
./bin/fabric-service-messages-list.sh ./test/org1
./bin/fabric-service-messages-list.sh ./test/org2
./bin/fabric-service-messages-list.sh ./test/org3

#Creating contract by ORG1

./bin/fabric-service-contract-create.sh ./test/org1 ./test/org1/contract-invite.json

#Checking messages avaliability by mupltyple parties
./bin/fabric-service-contracts-list.sh ./test/org1
./bin/fabric-service-contracts-list.sh ./test/org2

#Joining to contract by ORG2
./bin/fabric-service-contract-join.sh ./test/org2 "example" "org1"
