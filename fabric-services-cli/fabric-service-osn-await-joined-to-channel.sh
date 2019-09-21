#!/bin/bash

# Usage example:
# fabric-service-osn-await-joined-to-channel.sh ./test/org1/ osn1 service

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Waiting osn to join channel ..."

SERVICE_URL="localhost:${PROCESS_MANAGEMENT_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
http://${SERVICE_URL}/osn-await-joined-to-channel \
-d name=$2 \
-d channelName=$3

if [[ "$?" -ne 0 ]]; then
  echo "Failed to await osn joined to channel."
  exit 1
fi

echo "======================================================================"
