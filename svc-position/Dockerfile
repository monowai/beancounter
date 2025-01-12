FROM monowai/eclipse-temurin:21.0.5_11-jre-sentry

ARG JAR_FILE=build/libs/svc-position-0.1.1.jar
ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT=${GIT_COMMIT}
ENV VERSION=${VERSION}

WORKDIR /app

COPY ${JAR_FILE} position.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/position.jar"]
