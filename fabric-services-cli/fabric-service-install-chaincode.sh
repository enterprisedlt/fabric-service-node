#!/bin/bash

# Usage example:
# fabric-service-define-channel.sh ./test/org1/ service service 1.0.0

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings


echo "Installing chaincode ..."
REQUEST="{\"channelName\":\"$2\",\"chainCodeName\":\"$3\",\"chainCodeVersion\":\"$4\"}"

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
http://${SERVICE_URL}/install-chaincode \
-d $REQUEST

if [[ "$?" -ne 0 ]]; then
  echo "Failed to install chaincode."
  exit 1
fi

echo "======================================================================"
