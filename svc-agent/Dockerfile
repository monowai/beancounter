FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY build/libs/svc-agent-*.jar app.jar

# Expose port
EXPOSE 9530

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:9530/api/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
