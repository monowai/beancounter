FROM openjdk:16-oraclelinux8
#docker build . --build-arg JAR_FILE=build/libs/jar-shell-0.1.1.jar  -t monowai/bc-shell
#docker build . -t bc/shell

ARG JAR_FILE=build/libs/jar-shell-0.1.1.jar
ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT ${GIT_COMMIT}
ENV VERSION ${VERSION}
ENV API_PATH /secrets/google-api/

VOLUME /tmp

RUN mkdir /app && \
    mkdir /demo && \
    mkdir /import && \
    mkdir /secrets

WORKDIR /app

COPY ${JAR_FILE} app.jar
COPY src/test/resources/trades.csv /demo

RUN groupadd -r beancounter && useradd --no-log-init -r -g beancounter beancounter
USER beancounter

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]