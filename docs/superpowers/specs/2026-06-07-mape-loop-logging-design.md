# Spec: MDC-Driven Declarative MAPE-K Logging Design

- **Date**: 2026-06-07
- **Topic**: Refactoring MAPE-K Autonomic Loop Logging using MDC and Profile-based Formatting

## 1. Goal
Refactor the annotation-based logging approach for the AMoCNA core MAPE-K loop to eliminate prefix clutter (e.g. `[MAPE-LOOP][PHASE]`), leverage MDC (Mapped Diagnostic Context) for all correlation metadata, and dynamically adjust log output formats between development (human-readable with inline MDC) and production (ECS JSON formatted console logs). Additionally, reduce noise from the monitoring adapter and metis notifications.

## 2. Design Details

### 2.1 Clean Logging with MDC
The custom Aspect `LogLoopStepAspect` is refactored to:
1. Place loop metadata (`phase`, `step`, `correlationId`, `actionId`, `resourceName`) into the SLF4J MDC.
2. Log boundary notifications using plain English sentences, avoiding prefix tags.
3. Automatically restore previous MDC states upon method completion (success or failure) to maintain trace isolation.

#### Log Message Examples
- **Start**: `HTN Action Decomposition started`
- **Success**: `HTN Action Decomposition succeeded`
- **Failure**: `HTN Action Decomposition failed`

### 2.2 Noise Reduction in Monitoring Phase
- Individual anomaly triggers and clears (within `metrics-adapter` and `metis` notifications) will be downgraded to **`DEBUG`** level.
- `AnomalyScanner` in `metrics-adapter` will print a single **`INFO`** level batch execution summary when changes are actually performed, preventing console flooding.

### 2.3 Profile-Based Logging Configurations
Multi-profile configurations in `application.yml` files for `metis`, `metrics-adapter`, `palamedes`, and `themis`:

- **Default / Dev Profile**:
  Outputs standard human-readable logs containing correlation and action identifiers:
  ```yaml
  logging:
    pattern:
      console: "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} [corrId=%X{correlationId}, actId=%X{actionId}] - %msg%n"
  ```

- **Prod Profile** (triggered when `SPRING_PROFILES_ACTIVE=prod`):
  Uses ECS-structured JSON logging which automatically exports all MDC fields as queryable JSON fields:
  ```yaml
  logging:
    structured:
      format:
        console: "ecs"
  ```

### 2.4 Container & Deployment Profile Activation
- **Dockerfiles**:
  Refactored to define build arguments and default run environment:
  ```dockerfile
  ARG SPRING_PROFILES_ACTIVE=prod
  ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
  ```
- **Kubernetes Deployments**:
  Manifests in `infra/core/` are updated to explicitly inject `SPRING_PROFILES_ACTIVE=prod`.
- **Local Compose**:
  `docker-compose.yml` environment blocks continue to define `SPRING_PROFILES_ACTIVE=dev` to ensure local developers get readable console logs with MDC.

## 3. Scope & Target Files
The following files are targeted for updates:
- **Common Utility Library**:
  - `apps/core/common/src/main/java/com/kubiki/common/logging/LogLoopStepAspect.java`
- **Configuration Files**:
  - `apps/core/metis/src/main/resources/application.yml`
  - `apps/core/metrics-adapter/src/main/resources/application.yml`
  - `apps/core/palamedes/src/main/resources/application.yml`
  - `apps/core/themis/src/main/resources/application.yml`
- **Dockerfiles**:
  - `apps/core/metis/deployment/metis.dockerfile`
  - `apps/core/metrics-adapter/deployment/metrics-adapter.dockerfile`
  - `apps/core/palamedes/deployment/palamedes.dockerfile`
  - `apps/core/themis/deployment/themis.dockerfile`
- **Deployment Manifests**:
  - `infra/core/metis-02-deployment.yaml`
  - `infra/core/metrics-adapter-02-deployment.yaml`
  - `infra/core/palamedes-02-deployment.yaml`
  - `infra/core/themis-02-deployment.yaml`
