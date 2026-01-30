#!/bin/bash

set -e

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
JAR_FILE="vertx-service-launcher-$VERSION-fat.jar"
TAG="opendigitaleducation/vertx-service-launcher:$VERSION"
ARCHITECTURE="linux/arm64,linux/amd64"

BRANCH_NAME=`git branch | sed -n -e "s/^\* \(.*\)/\1/p"`
if [ "$BRANCH_NAME" = "master" ]; then
    LATEST_TAG="-t opendigitaleducation/vertx-service-launcher:latest"
else
    LATEST_TAG=""
fi

docker buildx build --push -t "$TAG" $LATEST_TAG . -f Dockerfile --build-arg JAR_FILE="$JAR_FILE" --platform $ARCHITECTURE
