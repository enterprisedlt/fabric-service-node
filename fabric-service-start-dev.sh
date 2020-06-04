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

. ${PROFILE_PATH}/settings

echo "Starting Fabric Service Node ..."
serviceID=`docker run -d \
 -e "PROFILE_PATH=${PROFILE_PATH}" \
 -e "ORG=${ORG}" \
 -e "DOMAIN=${DOMAIN}" \
 -e "ORG_LOCATION=${ORG_LOCATION}" \
 -e "ORG_STATE=${ORG_STATE}" \
 -e "LOG_LEVEL=${SERVICE_LOG_LEVEL:-INFO}" \
 -e "FABRIC_COMPONENTS_LOG_LEVEL=${FABRIC_COMPONENTS_LOG_LEVEL:-INFO}" \
 -e "ORG_COUNTRY=${ORG_COUNTRY}" \
 -e "CERTIFICATION_DURATION=${CERTIFICATION_DURATION}" \
 -e "SERVICE_BIND_PORT=${SERVICE_BIND_PORT}" \
 -e "COMPONENTS_DISTRIBUTOR_BIND_PORT=${COMPONENTS_DISTRIBUTOR_BIND_PORT}" \
 -e "SERVICE_EXTERNAL_ADDRESS=${SERVICE_EXTERNAL_ADDRESS}" \
 -e "ADMIN_PASSWORD=${ADMIN_PASSWORD}" \
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -e "LOG_FILE_SIZE=100m" \
 -e "LOG_MAX_FILES=5" \
 -p ${SERVICE_BIND_PORT}:${SERVICE_BIND_PORT} \
 -p ${COMPONENTS_DISTRIBUTOR_BIND_PORT}:${COMPONENTS_DISTRIBUTOR_BIND_PORT} \
 --log-driver json-file \
 --log-opt max-size=100m \
 --log-opt max-file=5 \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}/target/assembly:/opt/service \
 --volume=/var/run/:/host/var/run/ \
 --network=${FABRIC_SERVICE_NETWORK} \
 --name service.${ORG}.${DOMAIN} \
openjdk:8-jre java -jar /opt/service/service-node.jar`
echo "Service ID: ${serviceID}"

# await service node to start up
grep -m 1 "ServiceNode\$ - Started" <(docker logs -f ${serviceID} 2>&1)

echo "======================================================================"
