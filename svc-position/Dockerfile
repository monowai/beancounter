FROM eclipse-temurin:21.0.5_11-jre

ARG JAR_FILE=build/libs/svc-position-0.1.1.jar
ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT=${GIT_COMMIT}
ENV VERSION=${VERSION}

VOLUME /tmp

RUN mkdir /app
WORKDIR /app

EXPOSE 9500 9501

COPY ${JAR_FILE} app.jar
COPY lib/sentry-opentelemetry-agent-7.16.0.jar sentry-opentelemetry-agent.jar
ENV JAVA_OPTS=-javaagent:sentry-opentelemetry-agent.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
