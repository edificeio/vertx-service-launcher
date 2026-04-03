#!/bin/bash

set -e

VERSION=$(mvn -B help:evaluate -Dexpression=project.version -q -DforceStdout | sed 's/\x1b\[[0-9;]*m//g' | tr -d '[:cntrl:]')
JAR_FILE="vertx-service-launcher-$VERSION-fat.jar"
TAG="opendigitaleducation/vertx-service-launcher:$VERSION"
ARCHITECTURE="linux/arm64,linux/amd64"

BRANCH_NAME=`git branch | sed -n -e "s/^\* \(.*\)/\1/p"`
if [ "$BRANCH_NAME" = "master" ]; then
    LATEST_TAG="-t opendigitaleducation/vertx-service-launcher:latest"
else
    LATEST_TAG=""
fi

action="--push"
if [ "$1" == "local" ]; then
    action="--load"
fi

docker buildx build $action -t "$TAG" $LATEST_TAG . -f Dockerfile --build-arg JAR_FILE="$JAR_FILE" --platform $ARCHITECTURE

if [ "$action" == "--push" ]; then
    docker image ls | grep "launcher" | tr -s ' ' | cut -d' ' -f1  | xargs -r docker rmi -f
fi
