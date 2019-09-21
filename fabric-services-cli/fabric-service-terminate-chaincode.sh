#!/bin/bash

# Usage example:
# fabric-service-terminate-chaincode.sh ./test/org1/ peer0 service 1.0.0


if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Starting service to terminate chaincode ..."

SERVICE_URL="localhost:${PROCESS_MANAGEMENT_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
http://${SERVICE_URL}/terminate-chaincode \
-d peerName=$2 \
-d chainCodeName=$2 \
-d chainCodeVersion=$2

if [[ "$?" -ne 0 ]]; then
  echo "Failed to terminate chaincode."
  exit 1
fi

echo "======================================================================"
