#!/bin/bash

#
./clean-docker.sh

./build.sh
#
./bin/fabric-service-generate-static-env.sh ./test

# start ORG1
./fabric-service-start-dev.sh ./test/org1
./bin/fabric-service-get-user-key.sh ./test/org1 admin admin ./admin-org1.p12

# start ORG2
./fabric-service-start-dev.sh ./test/org2
./bin/fabric-service-get-user-key.sh ./test/org2 admin admin ./admin-org2.p12
