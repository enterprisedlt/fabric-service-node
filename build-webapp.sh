#!/bin/bash

rm -rf ./target/assembly/admin-console
###
sbt fastOptJS
mkdir ./target/assembly/admin-console
cp -r ./admin-console/bundle/* ./target/assembly/admin-console/