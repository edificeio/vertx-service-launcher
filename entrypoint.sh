#!/bin/bash
set -e

# Generate logging.properties with environment variable support
LOG_LEVEL="${LOG_LEVEL:-INFO}"
VERTX_LOG_LEVEL="${VERTX_LOG_LEVEL:-INFO}"
HAZELCAST_LOG_LEVEL="${HAZELCAST_LOG_LEVEL:-SEVERE}"
NETTY_LOG_LEVEL="${NETTY_LOG_LEVEL:-SEVERE}"
ACCESS_LOG_LEVEL="${ACCESS_LOG_LEVEL:-FINEST}"

cat > /srv/springboard/conf/logging.properties <<EOF
handlers=com.opendigitaleducation.launcher.logger.ENTLogHandler
com.opendigitaleducation.launcher.logger.ENTLogHandler.level=${LOG_LEVEL}

.level=${LOG_LEVEL}
org.vertx.level=${VERTX_LOG_LEVEL}
com.hazelcast.level=${HAZELCAST_LOG_LEVEL}
io.netty.util.internal.PlatformDependent.level=${NETTY_LOG_LEVEL}

ACCESS.level=${ACCESS_LOG_LEVEL}
ACCESS.handlers=com.opendigitaleducation.launcher.logger.AccessLogHandler
ACCESS.useParentHandlers=false
ACCESS.com.opendigitaleducation.launcher.logger.AccessLogHandler.level=${ACCESS_LOG_LEVEL}
ACCESS.com.opendigitaleducation.launcher.logger.AccessLogHandler.formatter=com.opendigitaleducation.launcher.logger.JsonAccessFormatter
EOF

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
