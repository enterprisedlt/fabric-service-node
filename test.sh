#!/bin/bash

checkCurl() {
  CURL_CODE=$(echo "${CURL_OUTPUT}" | tail -n 1)
  if [ ${CURL_CODE} -ne 200 ]; then
    echo "Occured error"
    exit 1
  fi
}

# before running this script do:
# export PATH=$PATH:<path to fabric-service-node/bin>

./clean-docker.sh

# build fresh chaincode and server
gradle clean
grep -m 1 "BUILD SUCCESSFUL" <(gradle service-node:shadowJar 2>&1)
grep -m 1 "BUILD SUCCESSFUL" <(gradle service-chain-code:shadowJar 2>&1)

# pack chain-code to deploy-able tarball
fabric-service-pack-chaincode.sh ./service-chain-code service-chain-code.tgz

# start ORG1
./fabric-service-start-dev.sh ./test/org1
CURL_OUTPUT=$(fabric-service-bootstrap.sh ./test/org1)
checkCurl
echo "======================================================================"
#CURL_OUTPUT=$(fabric-service-create-contract.sh ./test/org1 ./dummy-contract.json)


#fabric-service-create-user.sh ./test/org1 abc
#fabric-service-get-user-key.sh ./test/org1 abc abc ./abc.p12

# make invite from ORG1
CURL_OUTPUT=$(fabric-service-create-invite.sh ./test/org1 ./test/invite_1.json)
checkCurl
echo "======================================================================"
# join ORG2 using invite
CURL_OUTPUT=$(./fabric-service-start-dev.sh ./test/org2)
checkCurl
echo "======================================================================"
CURL_OUTPUT=$(fabric-service-join.sh ./test/org2 ./test/invite_1.json)
checkCurl
echo "======================================================================"

# make invite from ORG2
CURL_OUTPUT=$(fabric-service-create-invite.sh ./test/org2 ./test/invite_2.json)
checkCurl
echo "======================================================================"

# join ORG3 using invite
CURL_OUTPUT=$(./fabric-service-start-dev.sh ./test/org3)
checkCurl
echo "======================================================================"
CURL_OUTPUT=$(fabric-service-join.sh ./test/org3 ./test/invite_2.json)
checkCurl
echo "======================================================================"

# make invite from ORG3
CURL_OUTPUT=$(fabric-service-create-invite.sh ./test/org3 ./test/invite_3.json)
checkCurl
echo "======================================================================"

# join ORG2 using invite
CURL_OUTPUT=$(fabric-service-start-dev.sh ./test/org4)
checkCurl
echo "======================================================================"
CURL_OUTPUT=$(fabric-service-join.sh ./test/org4 ./test/invite_3.json)
checkCurl
echo "======================================================================"


CURL_OUTPUT=$(fabric-service-message-send.sh ./test/org1 org2 hey1)
checkCurl
echo "======================================================================"
CURL_OUTPUT=$(fabric-service-message-send.sh ./test/org2 org3 hey2)
checkCurl
echo "======================================================================"
CURL_OUTPUT=$(fabric-service-message-send.sh ./test/org3 org1 hey3)
checkCurl
echo "======================================================================"

CURL_OUTPUT=$(fabric-service-messages-list.sh ./test/org1)
checkCurl
echo "======================================================================"
CURL_OUTPUT=$(fabric-service-messages-list.sh ./test/org2)
checkCurl
echo "======================================================================"
CURL_OUTPUT=$(fabric-service-messages-list.sh ./test/org3)
checkCurl
echo "======================================================================"

