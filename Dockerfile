FROM openjdk:8-jre
MAINTAINER Damien BOISSIN <damien.boissin@opendigitaleducation.com>

ARG JAR_FILE

COPY target/${JAR_FILE} /opt/
RUN ln -s /opt/${JAR_FILE} /opt/vertx-service-launcher.jar && groupadd vertx && useradd -u 1000 -g 1000 -m vertx && mkdir /srv/springboard && mkdir /srv/storage && chown -R vertx:vertx /srv

USER vertx

WORKDIR /srv/springboard
EXPOSE 8090
VOLUME ["/srv/springboard/mods", "/srv/springboard/assets", "/srv/springboard/conf", "/home/vertx/.m2"]

CMD java -agentlib:jdwp=transport=dt_socket,address=5000,server=y,suspend=n -jar /opt/vertx-service-launcher.jar -Dvertx.services.path=/srv/springboard/mods -Dvertx.disableFileCaching=true -conf /srv/springboard/conf/vertx.conf

