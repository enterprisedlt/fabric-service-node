#!/bin/bash

for i in `docker ps -aq`; do docker stop $i; docker rm $i; done
docker volume prune -f

gradle clean service-node:shadowJar
