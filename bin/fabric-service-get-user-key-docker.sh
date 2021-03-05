#!/bin/bash

if [[ "$(uname)" = "Darwin" ]]; then
    PROFILE_PATH=$(greadlink -f "$1")
else
    PROFILE_PATH=$(readlink -f "$1")
fi


. ${PROFILE_PATH}/settings

echo "Obtaining user key ..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
docker run -it \
    --network=host \
    -v ${PROFILE_PATH}:${PROFILE_PATH} \
    -e ${PROFILE_PATH}=${PROFILE_PATH} \
    curlimages/curl:latest -k -G --silent --show-error \
    --cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
    --key ${PROFILE_PATH}/crypto/users/admin/admin.key \
    --request GET \
    --output "$4" \
    https://${SERVICE_URL}/admin/get-user-key \
    -d name=$2 \
    -d password=$3




if [[ "$?" -ne 0 ]]; then
  echo "Failed to obtain user key."
  exit 1
fi

echo "======================================================================"
