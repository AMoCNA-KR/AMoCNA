# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copy the schema directory (required for gRPC stubs)
COPY schema/ schema/

# Copy the metis module structure
COPY modules/metis/pom.xml modules/metis/
WORKDIR /app/modules/metis
RUN mvn dependency:go-offline

# Copy the source code using the correct path from project root
COPY modules/metis/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre as runtime
WORKDIR /app
COPY --from=builder /app/modules/metis/target/metis-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 50052

ENV SPRING_PROFILES_ACTIVE=dev
ENV METIS_GRAPHDB_URL=http://graphdb:7200
ENV METIS_PALAMEDES_HOST=palamedes

ENTRYPOINT ["java", "-jar", "app.jar"]
