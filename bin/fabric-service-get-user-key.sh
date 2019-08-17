#!/bin/bash

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Obtaining user key ..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl -k -G --silent --show-error -w %{http_code} \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
--output "$4" \
https://${SERVICE_URL}/admin/get-user-key \
-d name=$2 \
-d password=$3

if [[ "$?" -ne 0 ]]; then
  echo "Failed to obtain user key."
  exit 1
fi
