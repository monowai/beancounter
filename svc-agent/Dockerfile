FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY build/libs/svc-agent-*.jar app.jar

# Expose HTTP port and management port
EXPOSE 9530 9531

# Health check hits the management port so it is independent of the main
# server's context-path.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:9531/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
