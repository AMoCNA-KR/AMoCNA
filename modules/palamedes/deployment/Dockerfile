# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Install daedalus dependency first
COPY modules/daedalus/pom.xml modules/daedalus/
COPY modules/daedalus/src modules/daedalus/src/
WORKDIR /app/modules/daedalus
RUN mvn clean install -DskipTests

# Copy the palamedes module structure
WORKDIR /app
COPY modules/palamedes/pom.xml modules/palamedes/
WORKDIR /app/modules/palamedes
RUN mvn dependency:go-offline

# Copy the source code using the correct path from project root
COPY modules/palamedes/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=builder /app/modules/palamedes/target/palamedes-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENV SPRING_PROFILES_ACTIVE=dev
ENV SPRING_RABBITMQ_HOST=rabbitmq
ENV GRAPHDB_URL=http://graphdb:7200
ENV GRAPHDB_REPO=amocna
ENV PROMETHEUS_URL=http://prometheus:9090

ENTRYPOINT ["java", "-jar", "app.jar"]
