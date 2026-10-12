# ==============================================================================
# LegacyVault Spring Boot 3 + Java 21 Multi-Stage Dockerfile for Render
# ==============================================================================

# Stage 1: Build application JAR
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and compile production artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal, secure runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for Render health check if needed
RUN apk add --no-cache curl

# Create non-root user and persistent storage mount directory
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    mkdir -p /var/data/vault_files && \
    chown -R appuser:appgroup /var/data/vault_files /app

USER appuser

# Copy executable jar from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/legacyvault-backend-0.0.1-SNAPSHOT.jar app.jar

# Render injects PORT dynamically (defaults to 8080)
ENV PORT=8080
ENV VAULT_STORAGE_DIR=/var/data/vault_files
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
