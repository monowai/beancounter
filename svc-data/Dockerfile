FROM monowai/eclipse-temurin:21.0.5_11-jre-sentry

ARG JAR_FILE=build/libs/svc-data-0.1.1.jar

ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT=${GIT_COMMIT}
ENV VERSION=${VERSION}

EXPOSE 9510 9511 5005

WORKDIR /app

COPY build/libs/svc-data-0.1.1.jar app.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
#ENTRYPOINT ["java","-agentlib:jdwp=transport=dt_socket,address=*:5005,server=y,suspend=n","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
