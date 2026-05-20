# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copy the schema directory (required for gRPC stubs)
COPY libs/schema/ libs/schema/

# Copy the metis module structure
COPY apps/core/metis/pom.xml apps/core/metis/
WORKDIR /app/apps/core/metis
RUN mvn dependency:go-offline

# Copy the source code using the correct path from project root
COPY apps/core/metis/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=builder /app/apps/core/metis/target/*.jar app.jar

EXPOSE 50052

ENV SPRING_PROFILES_ACTIVE=dev
ENV METIS_GRAPHDB_URL=http://graphdb:7200
ENV METIS_PALAMEDES_HOST=palamedes

ENTRYPOINT ["java", "-jar", "app.jar"]
