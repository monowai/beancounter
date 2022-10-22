FROM amazoncorretto:17-alpine
#docker build . --build-arg JAR_FILE=build/libs/svc-data -t monowai/bc-data
ARG JAR_FILE=build/libs/svc-data-0.1.1.jar
#docker build . -t monowai/bc-data

ARG GIT_COMMIT
ARG VERSION

ENV GIT_COMMIT ${GIT_COMMIT}
ENV VERSION ${VERSION}

EXPOSE 9510 9511

VOLUME /tmp

RUN mkdir /app
WORKDIR /app

COPY ${JAR_FILE} app.jar
#RUN addgroup -S beancounter && adduser -S beancounter -G beancounter
#USER beancounter

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]