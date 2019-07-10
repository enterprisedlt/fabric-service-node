#!/bin/bash

if [[ "$(uname)" = "Darwin" ]]; then
    SCRIPT=$(greadlink -f "$0")
    HF_NET_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(greadlink -f "$1")
else
    SCRIPT=$(readlink -f "$0")
    HF_NET_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(readlink -f "$1")
fi

. ${PROFILE_PATH}/settings

echo "Joining organization to network ..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl --silent --show-error --request POST --data-binary "@$2"  http://${SERVICE_URL}/request-join
if [[ "$?" -ne 0 ]]; then
  echo "Failed to join network!"
  exit 1
fi

echo "======================================================================"
