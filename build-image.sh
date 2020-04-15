#!/bin/bash

./build.sh

docker build -t enterprisedlt/fabric-service-node:1.4.2-rc-7 .
docker tag enterprisedlt/fabric-service-node:1.4.2-rc-7 enterprisedlt/fabric-service-node
