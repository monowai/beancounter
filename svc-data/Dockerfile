FROM eclipse-temurin:21.0.5_11-jre
ARG JAR_FILE=build/libs/svc-data-0.1.1.jar
#docker build . -t monowai/bc-data:dev

ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT=${GIT_COMMIT}
ENV VERSION=${VERSION}

EXPOSE 9510 9511 5005

VOLUME /tmp

RUN mkdir /app
WORKDIR /app

COPY build/libs/svc-data-0.1.1.jar app.jar
COPY lib/sentry-opentelemetry-agent-7.16.0.jar sentry-opentelemetry-agent.jar
ENV JAVA_OPTS=-javaagent:sentry-opentelemetry-agent.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
#ENTRYPOINT ["java","-agentlib:jdwp=transport=dt_socket,address=*:5005,server=y,suspend=n","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
