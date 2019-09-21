#!/bin/bash

# Usage example:
# fabric-service-invoke-chaincode.sh ./test/org1/ service service get

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

REQUEST="{\"channelName\":\"$2\",\"chainCodeName\":\"$3\",\"functionName\":\"$4\"}"

echo "Invoking chaincode..."

SERVICE_URL="localhost:${PROXY_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
http://${SERVICE_URL}/invoke-chaincode \
-d $REQUEST

if [[ "$?" -ne 0 ]]; then
  echo "Failed to invoke chaincode."
  exit 1
fi

echo "======================================================================"
