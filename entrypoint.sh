#!/bin/bash
set -e

# If EXPORT_CONF_TEMPLATE is set to true then export template.j2 files contained in /srv/springboard/mods to EXPORT_CONF_TEMPLATE_PATH
if [ "$EXPORT_CONF_TEMPLATE" = "true" ]; then
    # If EXPORT_CONF_TEMPLATE_PATH is not set raise an error
    if [ -z "$EXPORT_CONF_TEMPLATE_PATH" ]; then
        echo "ERROR: EXPORT_CONF_TEMPLATE_PATH is not set. Please set it to the desired export path."
        exit 1
    fi
    cd /srv/springboard
    find ./mods -maxdepth 2 -mindepth 2 -type f -name "template.j2" | while read f; do \
        relpath=$(echo "$f" | sed 's|^\./mods/||'); \
        dir=$(dirname "$relpath"); \
        mkdir -p "$EXPORT_CONF_TEMPLATE_PATH/$dir"; \
        cp "$f" "$EXPORT_CONF_TEMPLATE_PATH/$relpath"; \
    done
    echo "The following templates were exported to $EXPORT_CONF_TEMPLATE_PATH"
    find "$EXPORT_CONF_TEMPLATE_PATH" -type f -name "template.j2"
    # If no template.j2 files were found, inform the user and exit
    if [ "$(find "$EXPORT_CONF_TEMPLATE_PATH" -type f -name "template.j2" | wc -l)" -eq 0 ]; then
        echo "No template.j2 files were found in /srv/springboard/mods."
        exit 1
    fi
    exit 0
fi

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
