#!/bin/bash

# Usage example:
# fabric-service-add-org-to-consortium.sh ./test/org1/ ./test/org1/add-org-to-consortium.json

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings



echo "Adding org to consortium ..."

SERVICE_URL="localhost:${ADMINISTRATION_SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request POST \
--data-binary "@$2" \
http://${SERVICE_URL}/add-org-to-consortium

if [[ "$?" -ne 0 ]]; then
    echo "Failed to add org to consortium."
    exit 1
fi

echo "======================================================================"
