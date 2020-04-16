#!/bin/bash

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi

. ${PROFILE_PATH}/settings

echo "Joining to channel $2..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
https://${SERVICE_URL}/admin/join-to-channel \
-d channelName=$2 \

if [[ "$?" -ne 0 ]]; then
  echo "Failed join to channel $2!"
  exit 1
fi

echo "======================================================================"
