# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Install daedalus dependency first
COPY apps/core/daedalus/pom.xml apps/core/daedalus/
COPY apps/core/daedalus/src apps/core/daedalus/src/
WORKDIR /app/apps/core/daedalus
RUN mvn clean install -DskipTests

# Copy the palamedes module structure
WORKDIR /app
COPY apps/core/palamedes/pom.xml apps/core/palamedes/
WORKDIR /app/apps/core/palamedes
RUN mvn dependency:go-offline

# Copy the source code using the correct path from project root
COPY apps/core/palamedes/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=builder /app/apps/core/palamedes/target/*.jar app.jar

EXPOSE 8081

ENV SPRING_PROFILES_ACTIVE=dev
ENV SPRING_RABBITMQ_HOST=rabbitmq
ENV GRAPHDB_URL=http://graphdb:7200
ENV GRAPHDB_REPO=amocna
ENV PROMETHEUS_URL=http://prometheus:9090

ENTRYPOINT ["java", "-jar", "app.jar"]
