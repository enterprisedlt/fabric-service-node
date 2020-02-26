#!/bin/bash

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Getting system block by number $3 at channel $2..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
--output "$4" \
https://${SERVICE_URL}/admin/get-user-key \
-d channelName={$2:-"service"} \
-d blockNumber={$3:-"0"}

if [[ "$?" -ne 0 ]]; then
  echo "Failed to get block."
  exit 1
fi

echo "======================================================================"
