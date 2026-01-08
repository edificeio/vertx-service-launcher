#!/bin/bash

set -e

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
JAR_FILE="vertx-service-launcher-$VERSION-fat.jar"
TAG="opendigitaleducation/vertx-service-launcher:$VERSION"
ARCHITECTURE="linux/amd64"

BRANCH_NAME=`git branch | sed -n -e "s/^\* \(.*\)/\1/p"`
#LATEST_TAG="-t opendigitaleducation/vertx-service-launcher:latest"
LATEST_TAG=""

docker buildx build --load -t "$TAG" $LATEST_TAG . -f Dockerfile --build-arg JAR_FILE="$JAR_FILE" --platform $ARCHITECTURE
echo "Loaded image : $TAG"
