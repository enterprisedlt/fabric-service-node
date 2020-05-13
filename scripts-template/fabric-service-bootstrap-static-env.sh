#!/bin/bash
set -e
PROFILE_PATH=${1:-.};
if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "${PROFILE_PATH}");
else
    PROFILE_PATH=$(readlink -f "${PROFILE_PATH}");
fi

ORG_LIST_FILE="${PROFILE_PATH}/shared/list";

if [[ ! -f ${ORG_LIST_FILE} ]]
then
  echo "Orgs list does not exist!"
  exit 1
fi


export BOX_MANAGER_BIND_PORT=3070
export FABRIC_SERVICE_NETWORK="fabric_service"
export BOX_MANAGER_NAME="single-box-mngr"

mkdir -p ${PROFILE_PATH}/box
touch ${PROFILE_PATH}/box/hosts

fabric-service-box-manager-start.sh ${PROFILE_PATH}/box
#
# Bootstrap first organization
#
FIRST_ORG=`head -n 1 ${ORG_LIST_FILE}`
pushd ${PROFILE_PATH}/${FIRST_ORG}
    fabric-service-start.sh .
    fabric-service-register-box-manager.sh . "default" "http://${BOX_MANAGER_NAME}:${BOX_MANAGER_BIND_PORT}"
    cat ./components.json |\
    jq '.block = {"maxMessageCount": 150, "absoluteMaxBytes": 103809024, "preferredMaxBytes": 524288, "batchTimeOut": "1s"}' |\
    jq '.raftSettings = {"tickInterval": "500ms", "electionTick": 10, "heartbeatTick": 1, "maxInflightBlocks": 5, "snapshotIntervalSize": 20971520}' |\
    jq '.networkName = "test_net"' > ./bootstrap.json

    fabric-service-bootstrap.sh . ./bootstrap.json

    fabric-service-create-invite.sh . ../shared/invite.json
popd


#
# Join others
#
OTHERS=`tail -n +2 ${ORG_LIST_FILE}`
for ORG in ${OTHERS}
do
    pushd ${PROFILE_PATH}/${ORG}
        fabric-service-start.sh .
        fabric-service-register-box-manager.sh . "default" "http://${BOX_MANAGER_NAME}:${BOX_MANAGER_BIND_PORT}"

        cp ../shared/invite.json ./invite.json
        jq '.invite = input' ./components.json ./invite.json > ./join.json

        fabric-service-join.sh . ./join.json
    popd
done
