FROM openjdk:16-oraclelinux8
#docker build . --build-arg JAR_FILE=build/libs/svc-position-0.1.1.jar -t monowai/bc-position

ARG JAR_FILE=build/libs/svc-position-0.1.1.jar
ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT ${GIT_COMMIT}
ENV VERSION ${VERSION}

VOLUME /tmp

RUN mkdir /app
WORKDIR /app

EXPOSE 9500 9501

RUN groupadd -r beancounter && useradd --no-log-init -r -g beancounter beancounter

COPY ${JAR_FILE} app.jar
COPY build/resources/main/application.yml .
USER beancounter

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]