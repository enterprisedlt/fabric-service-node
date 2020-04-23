#!/bin/bash
set -e

if [[ "$(uname)" = "Darwin" ]]; then
    SCRIPT=$(greadlink -f "$0")
    SERVICE_NODE_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(greadlink -f "$1")
else
    SCRIPT=$(readlink -f "$0")
    SERVICE_NODE_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(readlink -f "$1")
fi

echo "Starting docker box manager ..."
BOX_MANAGER_ID=`docker run -d \
 -e "PROFILE_PATH=${PROFILE_PATH}" \
 -e "BOX_MANAGER_NAME=${BOX_MANAGER_NAME}" \
 -e "BOX_MANAGER_BIND_PORT=${BOX_MANAGER_BIND_PORT}" \
 -e "BOX_MANAGER_EXTERNAL_ADDRESS=${SERVICE_EXTERNAL_ADDRESS}" \
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -e "FABRIC_SERVICE_NETWORK=${FABRIC_SERVICE_NETWORK}" \
 -p ${BOX_MANAGER_BIND_PORT}:${BOX_MANAGER_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=/var/run/:/host/var/run/ \
 --network=${FABRIC_SERVICE_NETWORK} \
 --name ${BOX_MANAGER_NAME} \
 --log-driver json-file \
 --log-opt max-size=100m \
 --log-opt max-file=5 \
 enterprisedlt/fabric-service-node:@FABRIC_SERVICE_NODE_VERSION@ \
 java -cp /opt/service/service-node.jar org.enterprisedlt.fabric.service.node.BoxManager`
echo "Box manager ID: ${BOX_MANAGER_ID}"

# await  box manager to start up
grep -m 1 "BoxManager\$ - Started" <(docker logs -f ${BOX_MANAGER_ID} 2>&1)

echo "======================================================================"
