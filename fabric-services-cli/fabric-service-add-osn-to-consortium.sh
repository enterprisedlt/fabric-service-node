#!/bin/bash

# Usage example:
# fabric-service-add-osn-to-consortium.sh ./test/org1/ osn1

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings


echo "Adding osn to channel ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
http://${SERVICE_URL}/add-osn-to-consortium \
-d osnName=$2

if [[ "$?" -ne 0 ]]; then
    echo "Failed to add osn to consortium."
    exit 1
fi

echo "======================================================================"
