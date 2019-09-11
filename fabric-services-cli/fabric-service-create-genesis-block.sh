#!/bin/bash

# Usage example:
# fabric-service-create-genesis-block.sh ./test/org1/ ./test/org1/bootstrap.json

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Creating genesis block ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
--data-binary "@$2" \
http://${SERVICE_URL}/create-genesis-block


if [[ "$?" -ne 0 ]]; then
    echo "Failed to create genesis block."
    exit 1
fi

echo "======================================================================"
