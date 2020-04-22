#!/bin/bash
set -e

#
./clean-docker.sh

./build.sh
#
./scripts-template/fabric-service-generate-static-env.sh ./test

export BOX_MANAGER_BIND_PORT=3070
export FABRIC_SERVICE_NETWORK="fabric_service"
export BOX_MANAGER_NAME="box-mngr-${BOX_MANAGER_BIND_PORT}"

mkdir -p ./test/box
touch ./test/box/hosts

./docker-box-mngr-start-dev.sh ./test/box

# start ORG1
./fabric-service-start-dev.sh ./test/org1

# start ORG2
#./fabric-service-start-dev.sh ./test/org2
