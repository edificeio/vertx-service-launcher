FROM openjdk:8-jre
LABEL maintainer="Damien BOISSIN <damien.boissin@opendigitaleducation.com>"

ARG JAR_FILE

COPY target/${JAR_FILE} /opt/
RUN ln -s /opt/${JAR_FILE} /opt/vertx-service-launcher.jar && groupadd vertx && useradd -u 1000 -g 1000 -m vertx && mkdir /srv/springboard && mkdir /srv/storage && chown -R vertx:vertx /srv

USER vertx

WORKDIR /srv/springboard
EXPOSE 8090

CMD java -agentlib:jdwp=transport=dt_socket,address=5000,server=y,suspend=n -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Dvertx.services.path=/srv/springboard/mods -Dvertx.disableFileCaching=true -Dconf=/srv/springboard/conf/vertx.conf -jar /opt/vertx-service-launcher.jar

