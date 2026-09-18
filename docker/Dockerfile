# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
ARG SERVICE
ENV MAVEN_OPTS="-Xmx512m -XX:ActiveProcessorCount=2"
WORKDIR /build
COPY ${SERVICE}/pom.xml ./pom.xml
COPY ${SERVICE}/src/main ./src/main
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    --mount=type=secret,id=corporate-ca \
    set -eu; \
    if [ -s /run/secrets/corporate-ca ]; then \
      cp "$JAVA_HOME/lib/security/cacerts" /tmp/build-cacerts; \
      keytool -importcert -noprompt -trustcacerts -alias cvd-corporate-ca \
        -file /run/secrets/corporate-ca -keystore /tmp/build-cacerts -storepass changeit; \
      export MAVEN_OPTS="$MAVEN_OPTS -Djavax.net.ssl.trustStore=/tmp/build-cacerts -Djavax.net.ssl.trustStorePassword=changeit"; \
    fi; \
    mvn -U -B -ntp -Dmaven.test.skip=true package && \
    rm -f /tmp/build-cacerts && \
    mkdir /out && cp target/*.jar /out/app.jar

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /out/app.jar ./app.jar
USER app
ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-jar", "/app/app.jar"]
