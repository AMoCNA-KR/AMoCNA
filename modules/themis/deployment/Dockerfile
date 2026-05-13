# Build stage
FROM maven:3.9.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copy the schema directory (required for gRPC stubs)
COPY schema/ schema/

# Copy the themis module structure
COPY modules/themis/pom.xml modules/themis/
WORKDIR /app/modules/themis
RUN mvn dependency:go-offline

# Copy the source code using the correct path from project root
# Note: Context is the project root (AMoCNA/)
COPY modules/themis/src ./src

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/modules/themis/target/themis-0.0.1-SNAPSHOT.jar app.jar

# Standard Spring Boot port and gRPC port
EXPOSE 8080 50051

# Environment variables with defaults
ENV SPRING_PROFILES_ACTIVE=dev
ENV SPRING_RABBITMQ_HOST=rabbitmq
ENV THEMIS_GRAPHDB_URL=http://graphdb:7200

ENTRYPOINT ["java", "-jar", "app.jar"]
