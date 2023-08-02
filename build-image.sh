#!/bin/bash

set -e

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
JAR_FILE="vertx-service-launcher-$VERSION-fat.jar"
TAG="jleobernard/vertx-service-launcher:$VERSION"
if [[ $(uname -m) == 'arm64' ]]; then
  echo "Building for arm64"
  DOCKERFILE="Dockerfile.arm64"
  ARCHITECTURE="linux/arm64"
else
  echo "Building for linux"
  DOCKERFILE="Dockerfile"
  ARCHITECTURE="linux/amd64"
fi

docker build -t "$TAG" . -f $DOCKERFILE --build-arg JAR_FILE="$JAR_FILE" --platform $ARCHITECTURE

#docker login
#docker push $TAG
