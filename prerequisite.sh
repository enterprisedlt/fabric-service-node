#!/bin/bash

FABRIC_VERSION=${1:-"1.4.2"}
FAB_SER_ND_IMAGE_TAG=${2:-"1.4.2-rc-3"}

RED='\033[0;31m'
BOLD='\033[0;1m'
ITALIC='\033[0;3m'
NC='\033[0m'

function checkComponentIsInstalled() {
  local component=$1
  local reference=${2:-component}
  command -v "$component" >>/dev/null 2>/dev/null
  if [[ $? -eq 1 ]]; then
    echo -e "\n${RED}${component} is not installed. please install ${reference}${NC}\n" && exit 1
  else
    echo -e "\n${ITALIC}$component${NC} installed"
  fi
}

function pullDockerImages() {
  echo -e "\n${BOLD}===> Checking availability hyperledger fabric docker images${NC}"
  curl -sSL http://bit.ly/2ysbOFE | bash -s -- $FABRIC_VERSION $FABRIC_VERSION 0.4.18 -s -b
  echo -e "\n"
  docker pull enterprisedlt/fabric-service-node:${FAB_SER_ND_IMAGE_TAG}
  echo -e "\n===> List out enterprisedlt docker images"
  docker images | grep enterprisedlt
}


echo -e "\n${BOLD}===> Checking essential software${NC}"
if [[ "$(uname)" == "Darwin" ]]; then
  checkComponentIsInstalled greadlink "by \"brew install coreutils\""
elif [[ "$(uname)" == "Linux" ]]; then
  checkComponentIsInstalled readlink
fi
checkComponentIsInstalled docker
checkComponentIsInstalled jq
checkComponentIsInstalled sbt
pullDockerImages
checkComponentIsInstalled fabric-service-bootstrap.sh "fabric-service-*.sh scripts should add to PATH variable"