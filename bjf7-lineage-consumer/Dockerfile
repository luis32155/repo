FROM northamerica-northeast2-docker.pkg.dev/nbrna-0109-anthosperu-cfc55dcf/pe-artifacts/ci/java:eclipse-17-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

ARG JAR_VERSION
ARG APP_NAME
ARG JAR_FILE=build/libs/bjf7-lineage-consumer-1.0.0.jar
COPY ${JAR_FILE} app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
