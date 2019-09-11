#!/bin/bash

# Usage example:
# fabric-service-add-osn-to-channel.sh ./test/org1/ service osn1

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

REQUEST="{\"channelName\":\"$3\",\"osnName\":\"$2\"}"

echo "Adding osn to channel ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
http://${SERVICE_URL}/add-osn-to-channel \
-d $REQUEST

if [[ "$?" -ne 0 ]]; then
    echo "Failed to add osn to channel."
    exit 1
fi

echo "======================================================================"
