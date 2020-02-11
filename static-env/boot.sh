#!/bin/bash
set -e

PROFILE_PATH=${1:-.};
if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "${PROFILE_PATH}");
else
    PROFILE_PATH=$(readlink -f "${PROFILE_PATH}");
fi

ORG_LIST_FILE="${PROFILE_PATH}/shared/list";

[[ -f ${ORG_LIST_FILE} ]] || (echo "Orgs list does not exist!" && exit 1);

#
# Bootstrap first organization
#
FIRST_ORG=`head -n 1 ${ORG_LIST_FILE}`
pushd ${PROFILE_PATH}/${FIRST_ORG}
    fabric-service-start.sh .
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

        cp ../shared/invite.json ./invite.json
        jq '.invite = input' ./components.json ./invite.json > ./join.json

        fabric-service-join.sh . ./join.json
    popd
done
