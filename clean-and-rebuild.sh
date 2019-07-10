#!/bin/bash

for i in `docker ps -aq`; do docker stop $i; docker rm $i; done
for i in `docker image ls | grep dev- | awk '{ print $1}'`; do docker image rm $i; done
docker volume prune -f

gradle clean service-node:shadowJar
