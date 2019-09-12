#!/bin/bash
# Usage example:
# fabric-service-create-create-user.sh ./test/org1/ user1

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Creating user ..."

SERVICE_URL="localhost:${IDENTITY_SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
http://${SERVICE_URL}/create-user \
-d name=$2

if [[ "$?" -ne 0 ]]; then
  echo "Failed to create invite."
  exit 1
fi

echo "======================================================================"
