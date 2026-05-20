# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Install daedalus dependency first
COPY apps/core/daedalus/pom.xml apps/core/daedalus/
COPY apps/core/daedalus/src apps/core/daedalus/src/
WORKDIR /app/apps/core/daedalus
RUN mvn clean install -DskipTests

# Copy the themis module structure
WORKDIR /app
COPY apps/core/themis/pom.xml apps/core/themis/
WORKDIR /app/apps/core/themis
RUN mvn dependency:go-offline

# Copy the source code using the correct path from project root
COPY apps/core/themis/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre as runtime
WORKDIR /app
COPY --from=builder /app/apps/core/themis/target/*.jar app.jar

EXPOSE 8080 50051

ENV SPRING_PROFILES_ACTIVE=dev
ENV SPRING_RABBITMQ_HOST=rabbitmq

ENTRYPOINT ["java", "-jar", "app.jar"]
