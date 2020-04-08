#!/bin/bash

#
./clean-docker.sh

./build.sh
#
./bin/fabric-service-generate-static-env.sh ./test

# start ORG1
./fabric-service-start-dev.sh ./test/org1

# start ORG2
./fabric-service-start-dev.sh ./test/org2
