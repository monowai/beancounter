FROM monowai/eclipse-temurin:21.0.5_11-jre-sentry

#docker build . -t monowai/bc-event
ARG JAR_FILE=build/libs/svc-event-0.1.1.jar

ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT=${GIT_COMMIT}
ENV VERSION=${VERSION}

EXPOSE 9520 9521
WORKDIR /app

COPY ${JAR_FILE} app.jar


ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
