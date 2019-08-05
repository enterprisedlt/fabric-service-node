#!/bin/bash

 if [ "$(uname)" = "Darwin" ]; then
    SCRIPT=$(greadlink -f "$0")
    HF_NET_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(greadlink -f "$1")
else
    SCRIPT=$(readlink -f "$0")
    HF_NET_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(readlink -f "$1")
fi


 . ${PROFILE_PATH}/settings

 echo "Sending message to $2..."
set -x
SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
-H "Content-Type: application/json" \
--request POST \
https://${SERVICE_URL}/service/send-message \
-d   "{\"to\":\"$2\",\"body\":\"$3\"}"
set +x
if [[ "$?" -ne 0 ]]; then
  echo "Failed to send message."
  exit 1
fi

 echo "======================================================================"