#!/bin/bash

./build.sh

docker build -t enterprisedlt/fabric-service-node:1.4.2-rc-5 .
docker tag enterprisedlt/fabric-service-node:1.4.2-rc-5 enterprisedlt/fabric-service-node
