#!/bin/bash

# Usage example:
# fabric-service-add-anchors-to-channel.sh ./test/org1/ service peer0

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

REQUEST="{\"channelName\":\"$3\",\"peerName\":\"$2\"}"

echo "Adding anchors to channel ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
http://${SERVICE_URL}/add-anchors-to-channel \
-d $REQUEST

if [[ "$?" -ne 0 ]]; then
    echo "Failed to add anchors to channel."
    exit 1
fi

echo "======================================================================"
