#!/bin/bash
set -e

#
./clean-docker.sh

./build.sh
#
./scripts-template/fabric-service-generate-static-env.sh ./test

# start ORG1
./fabric-service-start-dev.sh ./test/org1

# start ORG2
./fabric-service-start-dev.sh ./test/org2
