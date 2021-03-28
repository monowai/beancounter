FROM openjdk:16-ea-23-jdk-oraclelinux8
#docker build . --build-arg JAR_FILE=build/libs/svc-event -t monowai/bc-event
#docker build . -t monowai/bc-event
ARG JAR_FILE=build/libs/svc-event-0.1.1.jar

ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT ${GIT_COMMIT}
ENV VERSION ${VERSION}

EXPOSE 9520 9521

VOLUME /tmp

RUN mkdir /app
WORKDIR /app

COPY ${JAR_FILE} app.jar

RUN groupadd -r beancounter && useradd --no-log-init -r -g beancounter beancounter
USER beancounter

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]