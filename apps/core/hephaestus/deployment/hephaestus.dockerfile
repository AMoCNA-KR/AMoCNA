# ── Stage 1: Build Spring Boot Java Backend ──────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copy parent pom to resolve dependencies
COPY pom.xml .
RUN mvn install -N -B -ntp

# Install daedalus dependency first
COPY apps/core/daedalus/pom.xml apps/core/daedalus/
COPY apps/core/daedalus/src apps/core/daedalus/src/
WORKDIR /app/apps/core/daedalus
RUN mvn clean install -DskipTests -B -ntp

# Install common dependency
WORKDIR /app
COPY apps/core/common/pom.xml apps/core/common/
COPY apps/core/common/src apps/core/common/src/
WORKDIR /app/apps/core/common
RUN mvn clean install -DskipTests -B -ntp

# Copy hephaestus structure
WORKDIR /app
COPY apps/core/hephaestus/pom.xml apps/core/hephaestus/
WORKDIR /app/apps/core/hephaestus
RUN mvn dependency:go-offline -B -ntp

# Copy source code and build targets
COPY apps/core/hephaestus/src ./src

# Inject pre-compiled static assets directly from the host context
COPY apps/core/hephaestus/frontend/dist/ ./src/main/resources/static/

RUN mvn clean package -DskipTests -B -ntp

# ── Stage 2: Runtime Environment ─────────────────────────────────────
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=builder /app/apps/core/hephaestus/target/*.jar app.jar

EXPOSE 8086
ENV SPRING_PROFILES_ACTIVE=dev

ENTRYPOINT ["java", "-jar", "app.jar"]
