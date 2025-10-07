FROM ghcr.io/monowai/eclipse-temurin:21-jre-alpine-sentry7

ARG JAR_FILE=build/libs/svc-position-0.1.1.jar
ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT=${GIT_COMMIT}
ENV VERSION=${VERSION}

WORKDIR /app

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
