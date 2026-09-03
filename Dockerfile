ARG JAVA_FLAVOUR
FROM maven:3.9.3-eclipse-temurin-8-focal AS builder
WORKDIR /opt/vertx-service-launcher
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY ./migration ./migration
COPY ./src ./src
RUN mvn clean install -Dmaven.test.skip=true -DskipMavenDockerBuild

FROM eclipse-temurin:8-${JAVA_FLAVOUR}-focal
LABEL maintainer="Damien BOISSIN <damien.boissin@edifice.io>"

ARG JAR_FILE
COPY --from=builder /opt/vertx-service-launcher/target/${JAR_FILE} /opt/
COPY entrypoint.sh /srv/springboard/conf/entrypoint.sh
RUN chmod 777 /srv/springboard/conf/
RUN JAVA_EXT=$(find /opt/java/openjdk -type d -name 'ext' | head -1) && \
    JAVA_SEC=$(find /opt/java/openjdk -name 'java.security' | head -1) && \
    wget -O ${JAVA_EXT}/bcprov-jdk18on-171.jar https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.71/bcprov-jdk18on-1.71.jar && \
    echo "f3433a97d780fe9fa3dc3d562a41decd59b2e617ce884de9060349ac14750045  ${JAVA_EXT}/bcprov-jdk18on-171.jar" | sha256sum -c - && \
    echo "security.provider.10=org.bouncycastle.jce.provider.BouncyCastleProvider" >> ${JAVA_SEC}
RUN ln -s /opt/${JAR_FILE} /opt/vertx-service-launcher.jar && groupadd vertx && useradd -u 1000 -g 1000 -m vertx && mkdir /srv/storage && chown -R vertx:vertx /srv && chmod +x /srv/springboard/conf/entrypoint.sh
RUN apt-get update && apt-get install -y --no-install-recommends shared-mime-info && apt-get clean && rm -rf /var/lib/apt/lists/*

USER vertx
RUN mkdir -p /home/vertx/aaf
WORKDIR /srv/springboard
# EXPOSE 8090

ENTRYPOINT ["/srv/springboard/conf/entrypoint.sh"]
