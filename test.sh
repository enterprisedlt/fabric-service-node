#!/bin/bash

./clean-and-rebuild.sh

# start ORG1
./fabric-service-start.sh ./test/org1
./bootstrap.sh ./test/org1

# make invite from ORG1
./create-invite.sh ./test/org1 ./test/invite.json

# join ORG2 using invite
./fabric-service-start.sh ./test/org2
./join.sh ./test/org2 ./test/invite.json


