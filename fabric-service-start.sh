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
INITIAL_NAME="fabric.service.node.${SERVICE_BIND_PORT}"
serviceID=`docker run -d \
 -e "INITIAL_NAME=${INITIAL_NAME}" \
 -e "PROFILE_PATH=${PROFILE_PATH}" \
 -e "SERVICE_BIND_PORT=${SERVICE_BIND_PORT}" \
 -e "SERVICE_EXTERNAL_ADDRESS=${SERVICE_EXTERNAL_ADDRESS}" \
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -e "LOGS_PATH=/opt/service/${PROFILE_PATH}/data/${INITIAL_NAME}" \
 -p ${SERVICE_BIND_PORT}:${SERVICE_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}:/opt/service \
 --volume=${SERVICE_NODE_HOME}/service-chain-code:/opt/service-chain-code \
 --volume=${PROFILE_PATH}/data/${INITIAL_NAME}:/opt/service/${PROFILE_PATH}/data/${INITIAL_NAME} \
 --volume=/var/run/:/host/var/run/ \
 --name $INITIAL_NAME \
openjdk:8-jre /opt/service/scripts/with-logs.sh java -jar /opt/service/service-node/build/libs/service-node.jar`
echo "Service ID: ${serviceID}"

# await service node to start up
grep -m 1 "ServiceNode\$ - Started" <( tail -f ${PROFILE_PATH}/data/${INITIAL_NAME}/stdout.log)

echo "======================================================================"
