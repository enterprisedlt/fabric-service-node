#!/bin/bash

./build.sh

docker build -t enterprisedlt/fabric-service-node:1.4.2-rc-3 .
docker tag enterprisedlt/fabric-service-node:1.4.2-rc-3 enterprisedlt/fabric-service-node
