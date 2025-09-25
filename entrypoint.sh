#!/bin/bash
set -e

REMOTE_DEBUG=""
if [ "$ENABLE_REMOTE_DEBUG" = "true" ]; then
    REMOTE_DEBUG="-agentlib:jdwp=transport=dt_socket,address=5000,server=y,suspend=${DEBUG_SUSPEND:-n}"
fi

LOG_PROPS="-Djava.util.logging.config.file=/srv/springboard/conf/logging.properties"

if [ "$MODE" = "cluster" ]; then
    exec java $REMOTE_DEBUG $LOG_PROPS -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Dvertx.zookeeper.config=/srv/springboard/conf/zookeeper.json -jar /opt/vertx-service-launcher.jar -Dvertx.services.path=/srv/springboard/mods -Dvertx.disableFileCaching=true -conf /srv/springboard/conf/vertx.conf -cluster
else
    exec java $REMOTE_DEBUG $LOG_PROPS -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -jar /opt/vertx-service-launcher.jar -Dvertx.services.path=/srv/springboard/mods -Dvertx.disableFileCaching=true -conf /srv/springboard/conf/vertx.conf
fi
