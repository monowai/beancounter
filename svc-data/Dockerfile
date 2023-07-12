#FROM amazoncorretto:17-alpine3.17-jdk
FROM amazoncorretto:17
#docker build . --build-arg JAR_FILE=build/libs/svc-data -t monowai/bc-data
ARG JAR_FILE=build/libs/svc-data-0.1.1.jar
#docker build . -t monowai/bc-data:dev

ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT ${GIT_COMMIT}
ENV VERSION ${VERSION}

EXPOSE 9510 9511 9515

VOLUME /tmp

RUN mkdir /app
WORKDIR /app

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-agentlib:jdwp=transport=dt_socket,address=*:9515,server=y,suspend=n","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]