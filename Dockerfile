FROM eclipse-temurin:21-jre-alpine

# Sentry OpenTelemetry Agent Configuration
ENV SENTRY_VERSION=8.13.3
ENV SENTRY_AUTO_INIT="false"
ENV SENTRY_ENABLED="true"
ENV SENTRY_ENVIRONMENT="production"
ENV SENTRY_TRACES_SAMPLE_RATE="0.2"
# SENTRY_DSN provided via secrets in deployment

# OpenTelemetry Configuration
# Disable OTel exporters - Sentry agent handles export
ENV OTEL_JAVAAGENT_ENABLED="true"
ENV OTEL_SDK_DISABLED="false"
ENV OTEL_TRACES_EXPORTER="none"
ENV OTEL_METRICS_EXPORTER="none"
ENV OTEL_LOGS_EXPORTER="none"

WORKDIR /app

RUN apk add --no-cache --virtual .build-deps curl && \
    curl -o /app/sentry-opentelemetry-agent-${SENTRY_VERSION}.jar -L \
      https://repo1.maven.org/maven2/io/sentry/sentry-opentelemetry-agent/${SENTRY_VERSION}/sentry-opentelemetry-agent-${SENTRY_VERSION}.jar && \
    apk del .build-deps

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/sentry-opentelemetry-agent-${SENTRY_VERSION}.jar"
