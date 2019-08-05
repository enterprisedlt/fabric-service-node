#!/bin/bash
source common.sh
usageMsg="$0  [org profile dir]"
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

 echo "Fetching for messages ..."

SERVICE_URL="localhost:${SERVICE_BIND_PORT}"
curl -k -G --silent --show-error \
--key ${PROFILE_PATH}/crypto/users/admin/admin.key \
--cert ${PROFILE_PATH}/crypto/users/admin/admin.crt \
--request GET \
https://${SERVICE_URL}/service/list-messages

if [[ "$?" -ne 0 ]]; then
  echo "Failed to list messages."
  exit 1
fi

 echo "======================================================================"
fi