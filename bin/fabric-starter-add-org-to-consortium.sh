#!/bin/bash
set -e

PROFILE_PATH=${1:-.}
if [[ "$(uname)" == "Darwin" ]]; then
  PROFILE_PATH=$(greadlink -f "${PROFILE_PATH}")
else
  PROFILE_PATH=$(readlink -f "${PROFILE_PATH}")
fi


. ${PROFILE_PATH}/settings

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
--data-binary "@$2" https://${SERVICE_URL}/join-network

if [[ "$?" -ne 0 ]]; then
  echo "Failed add to consortium!"
  exit 1
fi
