#!/bin/bash

./clean-and-rebuild.sh
./fabric-service-start.sh ./test/org1
./bootstrap.sh ./test/org1
