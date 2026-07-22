ARG JAVA_FLAVOUR
FROM maven:3.9.16-eclipse-temurin-25 AS builder
WORKDIR /opt/vertx-service-launcher
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY ./migration ./migration
COPY ./src ./src
RUN mvn clean install -Dmaven.test.skip=true -DskipMavenDockerBuild

FROM eclipse-temurin:25.0.3_9-${JAVA_FLAVOUR}-jammy
LABEL maintainer="Damien BOISSIN <damien.boissin@edifice.io>"

ARG JAR_FILE
COPY --from=builder /opt/vertx-service-launcher/target/${JAR_FILE} /opt/
COPY entrypoint.sh /srv/springboard/conf/entrypoint.sh
RUN chmod 777 /srv/springboard/conf/
# BouncyCastle is registered as a JVM security provider so isolated verticle
# classloaders can use it. It is kept as its original signed jar (the JCE
# framework rejects a repackaged/unsigned provider) and added to the boot
# classpath in entrypoint.sh, since a -jar launch ignores -cp.
RUN apt-get update && apt-get install -y --no-install-recommends shared-mime-info wget \
    && mkdir -p /opt/libs \
    && wget -O /opt/libs/bcprov-jdk18on-1.80.jar https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.80/bcprov-jdk18on-1.80.jar \
    && echo "security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider" >> "${JAVA_HOME}/conf/security/java.security" \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
RUN ln -s /opt/${JAR_FILE} /opt/vertx-service-launcher.jar && groupadd vertx && useradd -u 1000 -g 1000 -m vertx && mkdir /srv/storage && chown -R vertx:vertx /srv && chmod +x /srv/springboard/conf/entrypoint.sh

USER vertx
RUN mkdir -p /home/vertx/aaf
WORKDIR /srv/springboard
# EXPOSE 8090

ENTRYPOINT ["/srv/springboard/conf/entrypoint.sh"]
