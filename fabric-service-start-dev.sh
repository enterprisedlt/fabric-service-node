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

echo "Starting Fabric Process Management Node ..."
#INITIAL_NAME="fabric.process-management.service.node.${PROCESS_MANAGEMENT_BIND_PORT}"
serviceID=`docker run -d \
 -e "INITIAL_NAME=${PROCESS_MANAGEMENT_HOST}" \
 -e "PROFILE_PATH=${PROFILE_PATH}" \
 -e "PROCESS_MANAGEMENT_BIND_PORT=${PROCESS_MANAGEMENT_BIND_PORT}" \
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -p ${PROCESS_MANAGEMENT_BIND_PORT}:${PROCESS_MANAGEMENT_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}/services/process-management-service/build/libs/process-management-service.jar:/opt/service/process-management-service.jar \
 --volume=/var/run/:/host/var/run/ \
 --name $PROCESS_MANAGEMENT_HOST \
openjdk:8-jre java -jar /opt/service/process-management-service.jar`
echo "Process Management Service ID: ${serviceID}"

# await process management to start up
grep -m 1 "ProcessManagementNode\$ - Started" <(docker logs -f ${serviceID} 2>&1)

echo "Starting Fabric Identity Node ..."
#INITIAL_NAME="fabric.identity.service.node.${IDENTITY_SERVICE_BIND_PORT}"
serviceID=`docker run -d \
 -e "IDENTITY_SERVICE_BIND_PORT=${IDENTITY_SERVICE_BIND_PORT}" \
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -p ${IDENTITY_SERVICE_BIND_PORT}:${IDENTITY_SERVICE_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}/services/identity-service/build/libs/identity-service.jar:/opt/service/identity-service.jar \
 --volume=/var/run/:/host/var/run/ \
 --name $IDENTITY_SERVICE_HOST \
 --network=$DOCKER_NETWORK \
openjdk:8-jre java -jar /opt/service/identity-service.jar`
echo "Identity Service ID: ${serviceID}"

# await identity node to start up
grep -m 1 "IdentityNode\$ - Started" <(docker logs -f ${serviceID} 2>&1)

echo "Starting Fabric Administration Node ..."
serviceID=`docker run -d \
 -e "ADMINISTRATION_SERVICE_BIND_PORT=${ADMINISTRATION_SERVICE_BIND_PORT}" \
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -p ${ADMINISTRATION_SERVICE_BIND_PORT}:${ADMINISTRATION_SERVICE_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}/services/administration-service/build/libs/administration-service.jar:/opt/service/administration-service.jar \
  --volume=${SERVICE_NODE_HOME}/service-chain-code/service-chain-code.tgz:/opt/service/service-chain-code.tgz \
 --volume=/var/run/:/host/var/run/ \
 --name $ADMINISTRATION_SERVICE_HOST \
 --network=$DOCKER_NETWORK \
openjdk:8-jre java -jar /opt/service/administration-service.jar`
echo "Administration Service ID: ${serviceID}"

# await maintenance node to start up
grep -m 1 "AdministrationNode\$ - Started" <(docker logs -f ${serviceID} 2>&1)

echo "Starting Fabric Maintenance Node ..."
serviceID=`docker run -d \
 -e "IDENTITY_SERVICE_BIND_PORT=${IDENTITY_SERVICE_BIND_PORT}" \
 -e "MAINTENANCE_SERVICE_BIND_PORT=${MAINTENANCE_SERVICE_BIND_PORT}" \
 -e "PROCESS_MANAGEMENT_BIND_PORT=${PROCESS_MANAGEMENT_BIND_PORT}" \
 -e "PROCESS_MANAGEMENT_HOST=${PROCESS_MANAGEMENT_HOST}" \
 -e "ADMINISTRATION_SERVICE_BIND_PORT=${ADMINISTRATION_SERVICE_BIND_PORT}" \
 -e "ADMINISTRATION_SERVICE_HOST=${ADMINISTRATION_SERVICE_HOST}" \
 -e "PROXY_SERVICE_BIND_PORT=${PROXY_SERVICE_BIND_PORT}" \
 -e "PROXY_SERVICE_HOST=${PROXY_SERVICE_HOST}" \
 -e "SERVICE_EXTERNAL_ADDRESS=${SERVICE_EXTERNAL_ADDRESS}"\
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -p ${MAINTENANCE_SERVICE_BIND_PORT}:${MAINTENANCE_SERVICE_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}/services/maintenance-service/build/libs/maintenance-service.jar:/opt/service/maintenance-service.jar \
 --volume=/var/run/:/host/var/run/ \
 --name $MAINTENANCE_SERVICE_HOST \
 --network=$DOCKER_NETWORK \
openjdk:8-jre java -jar /opt/service/maintenance-service.jar`
echo "Maintenance Service ID: ${serviceID}"

# await maintenance node to start up
grep -m 1 "MaintenanceNode\$ - Started" <(docker logs -f ${serviceID} 2>&1)

echo "Starting Fabric Proxy Node ..."
serviceID=`docker run -d \
 -e "PROXY_SERVICE_BIND_PORT=${PROXY_SERVICE_BIND_PORT}" \
 -e "SERVICE_EXTERNAL_ADDRESS=${SERVICE_EXTERNAL_ADDRESS}"\
 -e "DOCKER_SOCKET=unix:///host/var/run/docker.sock" \
 -p ${PROXY_SERVICE_BIND_PORT}:${PROXY_SERVICE_BIND_PORT} \
 --volume=${PROFILE_PATH}/hosts:/etc/hosts \
 --volume=${PROFILE_PATH}:/opt/profile \
 --volume=${SERVICE_NODE_HOME}/services/proxy-service/build/libs/proxy-service.jar:/opt/service/proxy-service.jar \
 --volume=/var/run/:/host/var/run/ \
 --name $PROXY_SERVICE_HOST \
 --network=$DOCKER_NETWORK \
openjdk:8-jre java -jar /opt/service/proxy-service.jar`
echo "Proxy Service ID: ${serviceID}"

# await maintenance node to start up
grep -m 1 "ProxyNode\$ - Started" <(docker logs -f ${serviceID} 2>&1)

if [[ ! -e ${PROFILE_PATH}/config/default.conf ]]; then
  mkdir -p ${PROFILE_PATH}/config/
  fabric-service-balancer-generate.sh ${PROFILE_PATH}
fi

echo "Starting Balancer Node ..."
#INITIAL_NAME="balancer.node.${BALANCER_BIND_PORT}"
nginxID=$(docker run -d \
  -p ${SERVICE_BIND_PORT}:${SERVICE_BIND_PORT} \
  --volume=${PROFILE_PATH}/hosts/:/etc/hosts \
  --volume=${PROFILE_PATH}/config/default.conf:/etc/nginx/conf.d/default.conf \
  --volume=${PROFILE_PATH}/crypto/service/tls/:/etc/nginx/keys/ \
  --name $SERVICE_HOST \
  --network=$DOCKER_NETWORK \
  nginx bash -c "nginx -g 'daemon off;'")
echo "Balancer ID: ${nginxID}"


echo "======================================================================"
