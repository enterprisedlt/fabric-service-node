#!/bin/bash

if [[ "$(uname)" = "Darwin" ]]; then
    SCRIPT=$(greadlink -f "$0")
    SERVICE_NODE_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(greadlink -f "$1")
else
    SCRIPT=$(readlink -f "$0")
    SERVICE_NODE_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(readlink -f "$1")
fi

. ${PROFILE_PATH}/settings

echo "Starting Fabric Service Node ..."

serviceID=`docker run -d \
 -e "PROFILE_PATH=${PROFILE_PATH}" \
 -e "SERVICE_BIND_PORT=${SERVICE_BIND_PORT}" \
 -e "SERVICE_EXTERNAL_PORT=${SERVICE_EXTERNAL_PORT}" \
 -e "SERVICE_EXTERNAL_IP=${SERVICE_EXTERNAL_IP}" \
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -p ${SERVICE_BIND_PORT}:${SERVICE_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}/chaincode:/opt/chaincode \
 --volume=${SERVICE_NODE_HOME}:/opt/service \
 --volume=/var/run/:/host/var/run/ \
 --name "fabric.service.node.${SERVICE_BIND_PORT}" \
openjdk:8-jre java -jar /opt/service/service-node/build/libs/service-node.jar`
echo "Service ID: ${serviceID}"

# await service node to start up
grep -m 1 "ServiceNode\$ - Started" <(docker logs -f ${serviceID} 2>&1)

echo "======================================================================"
