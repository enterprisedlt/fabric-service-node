#!/bin/bash
set -e

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Listing custom node component types ..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
https://${SERVICE_URL}/admin/list-custom-node-component-types \

if [[ "$?" -ne 0 ]]; then
  echo "Failed to list custom node component types."
  exit 1
fi

echo "======================================================================"
