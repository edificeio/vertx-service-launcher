FROM maven:3.9.9-eclipse-temurin-21 as builder
WORKDIR /opt/vertx-service-launcher
COPY pom.xml .
COPY ./src ./src
COPY ./migration ./migration
RUN mvn clean install -Dmaven.test.skip=true -DskipMavenDockerBuild

FROM eclipse-temurin:21-jre
LABEL maintainer="Damien BOISSIN <damien.boissin@edifice.io>"

ARG JAR_FILE
COPY --from=builder /opt/vertx-service-launcher/target/${JAR_FILE} /opt/
RUN apt-get update && apt-get install -y --no-install-recommends shared-mime-info wget && apt-get clean && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /opt/libs
RUN wget -O /opt/libs/bcprov-jdk18on-1.80.jar https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.80/bcprov-jdk18on-1.80.jar
RUN echo "security.provider.10=org.bouncycastle.jce.provider.BouncyCastleProvider" >> "${JAVA_HOME}/conf/security/java.security"
RUN ln -s /opt/${JAR_FILE} /opt/vertx-service-launcher.jar
#RUN groupadd vertx && useradd -u 1000 -g 1000 -m vertx
RUN mkdir /srv/springboard && mkdir /srv/storage && chown -R ubuntu:ubuntu /srv


USER ubuntu

WORKDIR /srv/springboard
EXPOSE 8090

CMD java -agentlib:jdwp=transport=dt_socket,address=*:5000,server=y,suspend=${DEBUG_SUSPEND:-n} -XX:+UnlockExperimentalVMOptions  -cp /opt/libs/bcprov-jdk18on-1.80.jar:/opt/vertx-service-launcher.jar -jar /opt/vertx-service-launcher.jar -Dvertx.services.path=/srv/springboard/mods -Dvertx.disableFileCaching=true -conf /srv/springboard/conf/vertx.conf
