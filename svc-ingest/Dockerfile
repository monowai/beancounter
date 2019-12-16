FROM openjdk:11-slim
#docker build . --build-arg JAR_FILE=build/libs/*.jar -t beancounter/svc-ingest
ARG JAR_FILE=build/libs/*.jar

EXPOSE 9520 9521

COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]