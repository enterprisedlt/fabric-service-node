#!/bin/bash
set -e

if [[ "$(uname)" = "Darwin" ]]; then
    SCRIPT=$(greadlink -f "$0")
    SERVICE_NODE_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(greadlink -f "$1")
else
    SCRIPT=$(readlink -f "$0")
    SERVICE_NODE_HOME=$(dirname "$SCRIPT")
    PROFILE_PATH=$(readlink -f "$1")
fi


echo "Starting custom node ..."

SERVICE_URL="localhost:3070"
curl --silent --show-error \
--request POST \
--data-binary "@$2"  http://${SERVICE_URL}/start-custom-node

if [[ "$?" -ne 0 ]]; then
  echo "Failed to start custom node!"
  exit 1
fi

echo "======================================================================"
