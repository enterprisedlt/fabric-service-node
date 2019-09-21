#!/bin/bash

# Usage example:
# fabric-service-create-channel.sh ./test/org1/ channel SampleConsortium org1

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

REQUEST="{\"channelName\":\"$2\",\"consortiumName\":\"$3\",\"orgName\":\"$4\"}"

echo "Creating channel ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
http://${SERVICE_URL}/create-channel \
-d $REQUEST

if [[ "$?" -ne 0 ]]; then
  echo "Failed to create channel."
  exit 1
fi

echo "======================================================================"
