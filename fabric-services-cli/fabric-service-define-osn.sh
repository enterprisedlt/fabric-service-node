#!/bin/bash

# Usage example:
# fabric-service-define-osn.sh ./test/org1/ osn1 7001

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

PEER_NODE="{\"name\":\"$2\",\"port\":\"$3\"}"

echo "Defining osn ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
http://${SERVICE_URL}/define-osn \
-d $PEER_NODE

if [[ "$?" -ne 0 ]]; then
  echo "Failed to define peer."
  exit 1
fi

echo "======================================================================"
