#!/bin/bash
set -e

rm -rf ./target/assembly/admin-console/*
###
sbt fastOptJS
mkdir -p ./target/assembly/admin-console
cp -r ./admin-console/bundle/* ./target/assembly/admin-console/