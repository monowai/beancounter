FROM openjdk:11-slim
#docker build . --build-arg JAR_FILE=build/libs/svc-position-0.0.1.jar -t bc/svc-position

ARG JAR_FILE=build/libs/svc-position-0.0.1.jar
ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT ${GIT_COMMIT}
ENV VERSION ${VERSION}

RUN mkdir /app
WORKDIR app

EXPOSE 9500 9501

COPY ${JAR_FILE} app.jar
COPY build/resources/main/application.yml .

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]