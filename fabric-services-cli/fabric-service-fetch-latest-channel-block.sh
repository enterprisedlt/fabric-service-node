#!/bin/bash

# Usage example:
# fabric-service-fetch-latest-channel-block.sh ./test/org1/ service

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings


echo "Fetching latest channel block ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
http://${SERVICE_URL}/fetch-latest-channel-block \
-d $2

if [[ "$?" -ne 0 ]]; then
  echo "Failed to fetch latest channel block."
  exit 1
fi

echo "======================================================================"
