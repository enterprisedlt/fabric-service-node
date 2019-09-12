#!/bin/bash

# Usage example:
# fabric-service-create-invite.sh ./test/org1/ invite.json

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Creating invite ..."

SERVICE_URL="localhost:${MAINTENANCE_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
--output "$2" http://${SERVICE_URL}/create-invite

if [[ "$?" -ne 0 ]]; then
  echo "Failed to create invite."
  exit 1
fi

echo "======================================================================"
