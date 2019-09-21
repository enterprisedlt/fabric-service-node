#!/bin/bash

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi

. ${PROFILE_PATH}/settings

echo "Starting Fabric Identity Node ..."
INITIAL_NAME="fabric.identity.service.node.${IDENTITY_SERVICE_BIND_PORT}"
serviceID=`docker run -d \
 -e "IDENTITY_SERVICE_BIND_PORT=${IDENTITY_SERVICE_BIND_PORT}" \
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -p ${IDENTITY_SERVICE_BIND_PORT}:${IDENTITY_SERVICE_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}/services/identity-service/build/libs/identity-service.jar:/opt/service/identity-service.jar \
 --volume=/var/run/:/host/var/run/ \
 --name $INITIAL_NAME \
openjdk:8-jre java -jar /opt/service/identity-service.jar`
echo "Identity Service ID: ${serviceID}"

echo "Starting Fabric Process Management Node ..."
INITIAL_NAME="fabric.process-management.service.node.${PROCESS_MANAGEMENT_BIND_PORT}"
serviceID=`docker run -d \
 -e "INITIAL_NAME=${INITIAL_NAME}" \
 -e "PROFILE_PATH=${PROFILE_PATH}" \
 -e "PROCESS_MANAGEMENT_BIND_PORT=${PROCESS_MANAGEMENT_BIND_PORT}" \
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -p ${PROCESS_MANAGEMENT_BIND_PORT}:${PROCESS_MANAGEMENT_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}/services/process-management-service/build/libs/process-management-service.jar:/opt/service/process-management-service.jar \
 --volume=/var/run/:/host/var/run/ \
 --name $INITIAL_NAME \
openjdk:8-jre java -jar /opt/service/process-management-service.jar`
echo "Process Management Service ID: ${serviceID}"

echo "Starting Fabric Service Node ..."
INITIAL_NAME="fabric.service.node.${SERVICE_BIND_PORT}"
serviceID=`docker run -d \
 -e "SERVICE_BIND_PORT=${SERVICE_BIND_PORT}" \
 -e "SERVICE_EXTERNAL_ADDRESS=${SERVICE_EXTERNAL_ADDRESS}" \
 -p ${SERVICE_BIND_PORT}:${SERVICE_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=/var/run/:/host/var/run/ \
 --name $INITIAL_NAME \
 enterprisedlt/fabric-service-node`
echo "Service ID: ${serviceID}"


# await service node to start up
grep -m 1 "ServiceNode\$ - Started" <(docker logs -f ${serviceID} 2>&1)

echo "Starting Balancer Node ..."
INITIAL_NAME="balancer.node.${BALANCER_BIND_PORT}"
nginxID=$(docker run -d \
  -p ${BALANCER_BIND_PORT}:${BALANCER_BIND_PORT} \
  --volume=${PROFILE_PATH}/hosts:/etc/hosts \
  --volume=${PROFILE_PATH}:/opt/profile \
  --volume=${PROFILE_PATH}/nginx/:/opt/profile \
  --volume=/var/run/:/host/var/run/ \
  --name $INITIAL_NAME \
  nginx bash -c "envsubst '$$ORG $$DOMAIN' < /templates/nginx.conf > /etc/nginx/conf.d/default.conf && cp /certs/* /etc/nginx/conf.d/ && nginx -g 'daemon off;'")
echo "Balancer ID: ${nginxID}"
echo "======================================================================"
