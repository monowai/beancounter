FROM openjdk:11-slim
#docker build . --build-arg JAR_FILE=build/libs/jar-shell-0.1.1.jar  -t bc/shell
#docker build . -t bc/shell

ARG JAR_FILE=build/libs/svc-shell-0.1.1.jar
ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT ${GIT_COMMIT}
ENV VERSION ${VERSION}
ENV API_PATH /secrets/google-api/

RUN mkdir /app && \
    mkdir /secrets

WORKDIR /app

COPY wait-for-it.sh .

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]