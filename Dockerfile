FROM openjdk:8-jre

COPY target/vertx-service-launcher-1.0-SNAPSHOT-fat.jar /opt/
RUN groupadd vertx && useradd -u 1000 -g 1000 -m vertx && mkdir /srv/springboard && mkdir /srv/storage && chown -R vertx:vertx /srv

USER vertx

WORKDIR /srv/springboard
EXPOSE 8090
VOLUME ["/srv/springboard/mods", "/srv/springboard/assets", "/srv/springboard/conf", "/home/vertx/.m2"]

CMD java -agentlib:jdwp=transport=dt_socket,address=5000,server=y,suspend=n -jar /opt/vertx-service-launcher-1.0-SNAPSHOT-fat.jar -Dvertx.services.path=/srv/springboard/mods -Dvertx.disableFileCaching=true -conf /srv/springboard/conf/vertx.conf

