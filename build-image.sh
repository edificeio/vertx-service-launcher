#!/bin/bash

set -e

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
JAR_FILE="vertx-service-launcher-$VERSION-fat.jar"
TAG="opendigitaleducation/vertx-service-launcher:$VERSION"
ARCHITECTURE="linux/arm64,linux/amd64"
docker buildx build --push -t "$TAG" . -f Dockerfile --build-arg JAR_FILE="$JAR_FILE" --platform $ARCHITECTURE
TAG="opendigitaleducation/vertx-service-launcher:$VERSION-jenkins"
#docker buildx build --push -t "$TAG" . -f Dockerfile.jenkins --build-arg JAR_FILE="$JAR_FILE" --platform $ARCHITECTURE
#docker login
