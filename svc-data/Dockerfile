FROM ghcr.io/monowai/eclipse-temurin:25-jre-alpine-sentry8.40.0

# Fail the build if the base image's bundled Sentry OpenTelemetry agent does
# not match the SDK version the app is compiled against. Prevents the kind of
# runtime NoSuchMethodError skew we hit when the FROM tag lagged behind the
# libs.versions.toml sentry version.
ARG EXPECTED_SENTRY_VERSION=8.40.0
RUN test -f /app/sentry-opentelemetry-agent-${EXPECTED_SENTRY_VERSION}.jar \
    || (echo "ERROR: expected sentry-opentelemetry-agent-${EXPECTED_SENTRY_VERSION}.jar in base image; got:" \
        && ls /app/sentry-opentelemetry-agent-*.jar 2>/dev/null \
        && exit 1)

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
