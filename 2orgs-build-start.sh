#!/bin/bash
set -e
export FABRIC_SERVICE_NETWORK="fabric_service"

#
./clean-docker.sh

./build.sh
#
./scripts-template/fabric-service-generate-static-env.sh ./test/config.csv ./test/net

export BOX_MANAGER_BIND_PORT=3070
export BOX_MANAGER_NAME="single-box-mngr-org1"
mkdir -p ./test/net/box-org1
touch ./test/net/box-org1/hosts
./fabric-service-box-manager-start-dev.sh ./test/net/box-org1
## start ORG1
./fabric-service-start-dev.sh ./test/net/org1
./scripts-template/fabric-service-register-box-manager.sh ./test/net/org1 "default" "http://${BOX_MANAGER_NAME}:${BOX_MANAGER_BIND_PORT}"
unset BOX_MANAGER_BIND_PORT BOX_MANAGER_NAME
###
export BOX_MANAGER_BIND_PORT=3071
export BOX_MANAGER_NAME="single-box-mngr-org2"
mkdir -p ./test/net/box-org2
touch ./test/net/box-org2/hosts
./fabric-service-box-manager-start-dev.sh ./test/net/box-org2
## start ORG2
./fabric-service-start-dev.sh ./test/net/org2
./scripts-template/fabric-service-register-box-manager.sh ./test/net/org2 "default" "http://${BOX_MANAGER_NAME}:${BOX_MANAGER_BIND_PORT}"
unset BOX_MANAGER_BIND_PORT BOX_MANAGER_NAME
