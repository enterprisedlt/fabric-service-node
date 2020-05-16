#!/bin/bash
set -e

sbt fastOptJS
rm -rf ./target/assembly/admin-console/*
mkdir -p ./target/assembly/admin-console
cp -r ./service-node/frontend/bundle/* ./target/assembly/admin-console/
