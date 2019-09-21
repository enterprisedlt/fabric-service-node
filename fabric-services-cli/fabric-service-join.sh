#!/bin/bash

# Usage example:
# fabric-service-join.sh ./test/org2/ ./test/invite_1.json

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi

. ${PROFILE_PATH}/settings

echo "Joining organization to network ..."

SERVICE_URL="localhost:${MAINTENANCE_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
--data-binary "@$2"  http://${SERVICE_URL}/request-join

if [[ "$?" -ne 0 ]]; then
  echo "Failed to join network!"
  exit 1
fi

echo "======================================================================"
