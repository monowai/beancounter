FROM eclipse-temurin:25-jre-alpine

# Sentry OpenTelemetry Agent Configuration
ENV SENTRY_VERSION=8.40.0
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

# Patch OS packages in the base layer first. Temurin's published
# 25-jre-alpine tag lags Alpine security bumps, so `apk upgrade` pulls the
# fixed p11-kit (SNYK-ALPINE323-P11KIT-17486696, High) and expat
# (SNYK-ALPINE323-EXPAT-17675120, Medium) now rather than waiting for the
# upstream image rebuild. Propagates to every service via this shared base.
RUN apk --no-cache upgrade && \
    apk add --no-cache --virtual .build-deps curl && \
    curl -o /app/sentry-opentelemetry-agent-${SENTRY_VERSION}.jar -L \
      https://repo1.maven.org/maven2/io/sentry/sentry-opentelemetry-agent/${SENTRY_VERSION}/sentry-opentelemetry-agent-${SENTRY_VERSION}.jar && \
    apk del .build-deps

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/sentry-opentelemetry-agent-${SENTRY_VERSION}.jar"
