#!/bin/bash

FABRIC_VERSION=${1:-"1.4.2"}
FAB_SER_ND_IMAGE_TAG=${2:-"1.4.2-rc-3"}


function checkComponentIsInstalled() {
  local component=$1
  local reference=${2:-component}
  command -v "$component" >>/dev/null 2>/dev/null
  [ $? -eq 1 ] && echo -e "$component is not installed. please install ${reference}"
}

function pullDockerImages() {
  curl -sSL http://bit.ly/2ysbOFE | bash -s -- $FABRIC_VERSION $FABRIC_VERSION 0.4.18 -s -b
  docker pull enterprisedlt/fabric-service-node:${FAB_SER_ND_IMAGE_TAG}
  echo "===> List out enterprisedlt docker images"
  docker images | grep enterprisedlt
}


if [[ "$(uname)" == "Darwin" ]]; then
  checkComponentIsInstalled greadlink "by brew install coreutils"
elif [[ "$(uname)" == "Linux" ]]; then
  checkComponentIsInstalled readlink
fi
checkComponentIsInstalled docker
checkComponentIsInstalled jq
checkComponentIsInstalled gradle
pullDockerImages
checkComponentIsInstalled fabric-service-bootstrap.sh "should add \$PROJECT_DIR/bin to PATH variable"