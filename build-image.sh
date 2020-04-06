#!/bin/bash

./build.sh

docker build -t enterprisedlt/fabric-service-node:1.4.2-rc-4 .
docker tag enterprisedlt/fabric-service-node:1.4.2-rc-4 enterprisedlt/fabric-service-node
