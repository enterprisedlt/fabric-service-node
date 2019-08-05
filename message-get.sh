#!/bin/bash
source common.sh
usageMsg="$0  [org profile dir] [sender org name] [message key]"
if [ ! -d "$1" ]; then
    printUsage "$usageMsg"
else

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

 echo "Getting message by key $3..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl -k --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
-H "Content-Type: application/json" \
--request POST \
https://${SERVICE_URL}/service/get-message \
-d   "{\"sender\":\"$2\",\"messageKey\":\"$3\"}"

if [[ "$?" -ne 0 ]]; then
  echo "Failed to get message."
  exit 1
fi

 echo "======================================================================"
fi