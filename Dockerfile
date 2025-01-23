FROM maven:3.9.3-eclipse-temurin-8-focal as builder
WORKDIR /opt/vertx-service-launcher
COPY pom.xml .
COPY ./src ./src
COPY ./migration ./migration
RUN mvn clean install -Dmaven.test.skip=true -DskipMavenDockerBuild

FROM eclipse-temurin:8-jre-focal
LABEL maintainer="Damien BOISSIN <damien.boissin@edifice.io>"

ARG JAR_FILE
COPY --from=builder /opt/vertx-service-launcher/target/${JAR_FILE} /opt/
RUN wget -O /opt/java/openjdk/lib/ext/bcprov-jdk15on-160.jar https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.60/bcprov-jdk15on-1.60.jar && echo "security.provider.10=org.bouncycastle.jce.provider.BouncyCastleProvider" >> /opt/java/openjdk/lib/security/java.security
RUN ln -s /opt/${JAR_FILE} /opt/vertx-service-launcher.jar && groupadd vertx && useradd -u 1000 -g 1000 -m vertx && mkdir /srv/springboard && mkdir /srv/storage && chown -R vertx:vertx /srv
RUN apt-get update && apt-get install -y --no-install-recommends shared-mime-info && apt-get clean && rm -rf /var/lib/apt/lists/*

USER vertx

WORKDIR /srv/springboard
EXPOSE 8090

CMD java -agentlib:jdwp=transport=dt_socket,address=5000,server=y,suspend=${DEBUG_SUSPEND:-n} -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -jar /opt/vertx-service-launcher.jar -Dvertx.services.path=/srv/springboard/mods -Dvertx.disableFileCaching=true -conf /srv/springboard/conf/vertx.conf
