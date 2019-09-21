#!/bin/bash

# Usage example:
# fabric-service-start-peer.sh ./test/org1/ peer0

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Starting peer node ..."

SERVICE_URL="localhost:${PROCESS_MANAGEMENT_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
http://${SERVICE_URL}/start-peer-node \
-d name=$2

if [[ "$?" -ne 0 ]]; then
  echo "Failed to start peer node."
  exit 1
fi

echo "======================================================================"
