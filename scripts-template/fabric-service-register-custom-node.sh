#!/bin/bash
set -e

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Registering custom component type ..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
https://${SERVICE_URL}/admin/register-custom-node-component-type \
-d boxName=$2 \
-d componentTypeName=$3

if [[ "$?" -ne 0 ]]; then
  echo "Failed to registry custom component $3."
  exit 1
fi

echo "======================================================================"
