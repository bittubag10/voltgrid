# =============================================================================
# Stage 1: Build application with Maven and Eclipse Temurin JDK 21
# =============================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests

# =============================================================================
# Stage 2: Production Runtime with Eclipse Temurin JRE 21
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as a non-privileged user for security
RUN addgroup -S voltgrid && adduser -S voltgrid -G voltgrid

# Copy executable jar from builder stage
COPY --from=builder --chown=voltgrid:voltgrid /build/target/voltgrid-*.jar app.jar

USER voltgrid:voltgrid

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
