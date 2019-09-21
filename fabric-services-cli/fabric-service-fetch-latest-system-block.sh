#!/bin/bash

# Usage example:
# fabric-service-fetch-latest-system-block.sh ./test/org1/

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings


echo "Fetching latest system block ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
http://${SERVICE_URL}/fetch-latest-system-block

if [[ "$?" -ne 0 ]]; then
  echo "Failed to fetch latest system block."
  exit 1
fi

echo "======================================================================"
