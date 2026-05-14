# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copy the schema directory (required for gRPC stubs)
COPY schema/ schema/

# Copy the palamedes module structure
COPY modules/palamedes/pom.xml modules/palamedes/
WORKDIR /app/modules/palamedes
RUN mvn dependency:go-offline

# Copy the source code using the correct path from project root
COPY modules/palamedes/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre as runtime
WORKDIR /app
COPY --from=builder /app/modules/palamedes/target/palamedes-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080 50051

ENV SPRING_PROFILES_ACTIVE=dev
ENV SPRING_RABBITMQ_HOST=rabbitmq
ENV PALAMEDES_GRAPHDB_URL=http://graphdb:7200

ENTRYPOINT ["java", "-jar", "app.jar"]
