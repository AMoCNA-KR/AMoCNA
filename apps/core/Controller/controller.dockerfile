# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy the Controller source code
COPY apps/core/Controller/ /app/
RUN mvn clean package -DskipTests -B -ntp

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
