#!/bin/bash

# You are assumed to have already built the services from ../ with ./gradlew build
cd ../svc-data || exit
docker build . -t bc/svc-data &

cd ../svc-position || exit
docker build . -t bc/svc-position &

cd ../jar-shell || exit
docker build . -t bc/shell &

# bc-view takes way longer than the services to build, so we just wait for that to finish
cd ../bc-view || exit
docker build . -t bc/app

