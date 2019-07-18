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

echo "Creating invite ..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl --silent --show-error --request GET --output "$2" http://${SERVICE_URL}/create-invite
if [[ "$?" -ne 0 ]]; then
  echo "Failed to create invite."
  exit 1
fi

echo "======================================================================"
