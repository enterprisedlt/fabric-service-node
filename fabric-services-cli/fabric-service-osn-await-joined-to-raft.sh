#!/bin/bash

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Waiting osn to join raft ..."

SERVICE_URL="localhost:${PROCESS_MANAGEMENT_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
http://${SERVICE_URL}/osn-await-joined-to-raft \
-d name=$2

if [[ "$?" -ne 0 ]]; then
  echo "Failed to await osn joined to raft."
  exit 1
fi

echo "======================================================================"
