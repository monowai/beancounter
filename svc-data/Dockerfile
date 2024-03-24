FROM amazoncorretto:21-al2023-jdk
ARG JAR_FILE=build/libs/svc-data-0.1.1.jar
#docker build . -t monowai/bc-data:dev

ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT ${GIT_COMMIT}
ENV VERSION ${VERSION}

EXPOSE 9510 9511 5005

VOLUME /tmp

RUN mkdir /app
WORKDIR /app

COPY build/libs/svc-data-0.1.1.jar app.jar

ENTRYPOINT ["java","-agentlib:jdwp=transport=dt_socket,address=*:5005,server=y,suspend=n","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]