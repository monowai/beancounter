FROM openjdk:11-slim
#docker build . --build-arg JAR_FILE=build/libs/svc-data -t beancounter/svc-data
ARG JAR_FILE=build/libs/*.jar

EXPOSE 9510 9511

COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]