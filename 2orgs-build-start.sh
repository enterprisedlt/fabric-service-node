#!/bin/bash
set -e

#
./clean-docker.sh

./build.sh
#
./scripts-template/fabric-service-generate-static-env.sh ./test/config.csv ./test/net

export BOX_MANAGER_BIND_PORT=3070
export FABRIC_SERVICE_NETWORK="fabric_service"
export BOX_MANAGER_NAME="single-box-mngr"
# http://single-box-mngr:3070
mkdir -p ./test/net/box
touch ./test/net/box/hosts

./fabric-service-box-manager-start-dev.sh ./test/net/box

# start ORG1
./fabric-service-start-dev.sh ./test/net/org1
./scripts-template/fabric-service-register-box-manager.sh ./test/net/org1 "default" "http://${BOX_MANAGER_NAME}:${BOX_MANAGER_BIND_PORT}"

# start ORG2
./fabric-service-start-dev.sh ./test/net/org2
./scripts-template/fabric-service-register-box-manager.sh ./test/net/org2 "default" "http://${BOX_MANAGER_NAME}:${BOX_MANAGER_BIND_PORT}"
