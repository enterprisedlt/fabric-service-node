#!/bin/bash

# Usage example:
# fabric-service-instantiate-chaincode.sh ./test/org1/ ./test/org1/instantiate.json

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings


echo "Instantiate chaincode ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
--data-binary "@$2" \
http://${SERVICE_URL}/instantiate-chaincode


if [[ "$?" -ne 0 ]]; then
  echo "Failed to install chaincode."
  exit 1
fi

echo "======================================================================"
