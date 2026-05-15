# Metis — Monitor Module

Metis is the **Monitor** phase of the AMoCNA (Autonomic Management of Cloud-Native Applications) MRE-K autonomic loop. It receives structural discovery events from sensors over gRPC, translates them into SPARQL updates against a GraphDB knowledge base, and triggers the Palamedes reasoning module after each successful batch.

Metis does **not** store raw metric values (CPU, memory, latency). Those live exclusively in Prometheus TSDB. Metis only persists structural and topological knowledge: entity types, relationships, lifecycle states, and metric endpoint metadata.

---

## Table of Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Building](#building)
- [Docker](#docker)
- [Running](#running)
- [Testing](#testing)
- [gRPC API](#grpc-api)
- [Package Structure](#package-structure)

---

## Architecture

```
Sensors
  │  IngestBatch (gRPC, port 50052)
  ▼
SensorIngestionGrpcService
  │
  ├─► SensorEventProcessor
  │     └─► SensorEventHandler (one per event type)
  │           └─► KnowledgeBaseWriter ──► GraphDB (SPARQL over HTTP)
  │
  └─► PalamedesNotifier ──► Palamedes ReasonerService (gRPC, port 50051)
```

Five event types are supported, each handled by a dedicated handler:

| Event | Handler | Graph operation |
|---|---|---|
| `EntityDiscoveredEvent` | `EntityDiscoveredHandler` | INSERT entity triples |
| `RelationshipAssertedEvent` | `RelationshipAssertedHandler` | INSERT relationship + inverse/symmetric triples |
| `StateChangedEvent` | `StateChangedHandler` | Atomic DELETE/INSERT for `hasCurrentState` |
| `EntityDeletedEvent` | `EntityDeletedHandler` | DELETE all triples for the entity |
| `MetricMetadataRegisteredEvent` | `MetricMetadataRegisteredHandler` | INSERT metric endpoint metadata |

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 25 |
| Maven | 3.9+ |
| GraphDB | 10.8.x (or compatible RDF4J 4.3.x endpoint) |
| Palamedes | running and reachable on the configured host/port |
| `protoc` | resolved automatically by `protobuf-maven-plugin` |

---

## Configuration

All properties are under the `metis` prefix in `application.yml`.

```yaml
grpc:
  server:
    port: 50052          # gRPC server port for incoming sensor events

metis:
  graphdb:
    url: "http://graphdb:7200"   # GraphDB base URL
    repositoryId: "amocna"       # Repository name
    timeoutMs: 5000              # Connect + read timeout in milliseconds
  ontology:
    cneeNamespace: "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#"
  palamedes:
    host: "palamedes"    # Palamedes service hostname
    port: 50051          # Palamedes gRPC port
```

Override any property via environment variable using Spring's relaxed binding, e.g.:

```bash
METIS_GRAPHDB_URL=http://localhost:7200 \
METIS_PALAMEDES_HOST=localhost \
java -jar metis.jar
```

Or pass them as JVM arguments:

```bash
java -Dmetis.graphdb.url=http://localhost:7200 \
     -Dmetis.palamedes.host=localhost \
     -jar metis.jar
```

---

## Building

Proto stubs are generated from `../../schema/metis.proto` and `../../schema/palamedes.proto` at compile time by `protobuf-maven-plugin`. Run from the `modules/metis` directory:

```bash
# Compile and package (skipping tests)
mvn package -DskipTests

# Compile only
mvn compile
```

The fat JAR is produced at `target/metis-0.0.1-SNAPSHOT.jar`.

> **Note:** The build requires `protoc` and the gRPC Java plugin. Both are downloaded automatically by the Maven plugin — no manual installation needed.

---

## Docker

The Dockerfile is a two-stage build. Stage 1 compiles the JAR inside a Maven container (including proto code generation from `../../schema/`). Stage 2 produces a lean JRE-only runtime image.

The build context must be the **repo root** so that both `schema/` and `modules/metis/` are available to the builder stage. The `build.sh` script handles this automatically.

### Build the image

```bash
# From the modules/metis directory
./build.sh

# Or from the repo root
scripts/build_all.sh          # builds all modules including metis
```

The default registry prefix is `sglomski`. Override it with the `REGISTRY` env var:

```bash
REGISTRY=myregistry ./build.sh
```

### Push to a registry

```bash
./build.sh --push

# With a custom registry
REGISTRY=myregistry ./build.sh --push
```

### Run the container

```bash
docker run -p 50052:50052 \
  -e METIS_GRAPHDB_URL=http://graphdb:7200 \
  -e METIS_PALAMEDES_HOST=palamedes \
  sglomski/metis:latest
```

Pass any Spring property as an environment variable using uppercase with underscores (Spring's relaxed binding), or as a `JAVA_TOOL_OPTIONS` JVM argument:

```bash
docker run -p 50052:50052 \
  -e JAVA_TOOL_OPTIONS="-Dmetis.graphdb.url=http://graphdb:7200 -Dmetis.palamedes.host=palamedes" \
  sglomski/metis:latest
```

---

## Running

### Locally (JAR)

```bash
# With default config (expects GraphDB at graphdb:7200 and Palamedes at palamedes:50051)
java -jar target/metis-0.0.1-SNAPSHOT.jar

# Pointing at local services
java -Dmetis.graphdb.url=http://localhost:7200 \
     -Dmetis.palamedes.host=localhost \
     -jar target/metis-0.0.1-SNAPSHOT.jar
```

### With Maven (dev mode)

```bash
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dmetis.graphdb.url=http://localhost:7200 -Dmetis.palamedes.host=localhost"
```

Once started, Metis listens for gRPC connections on port **50052** (configurable via `grpc.server.port`).

---

## Testing

All tests run with a single command from the `modules/metis` directory:

```bash
mvn test
```

This runs both property-based tests and integration tests. No external services are required — GraphDB is simulated by WireMock and Palamedes by an in-process gRPC server.

### Test layout

```
src/test/java/com/kubiki/metis/
├── pbt/                          # Property-based tests (jqwik, 100 tries each)
│   ├── NoRawMetricValuesPropertyTest     # P1: no ^^xsd:double or ^^xsd:float in any SPARQL
│   ├── EntityDiscoveredPropertyTest      # P2: mandatory triples + CNEEOnt type conformance
│   ├── StateChangedPropertyTest          # P3: exactly one hasCurrentState triple after each change
│   ├── InversePropertyTest               # P4: inverse triples for contains/isPartOf/hosts/isHostedOn
│   ├── SymmetricPropertyTest             # P5: both directions of communicatesWith
│   ├── PalamededsTriggerPropertyTest     # P6: triggerUpdate called once per successful batch
│   └── IdempotencyPropertyTest           # P7: triple count unchanged after re-sending same event
└── integration/
    ├── SensorIngestionGrpcServiceIT      # Full Spring context + WireMock GraphDB
    └── PalamedesNotifierIT               # In-process gRPC Palamedes stub
```

### Property-based tests (PBT)

The PBT suite uses [jqwik](https://jqwik.net/) 1.9.1. Each `@Property` method runs 100 randomised trials plus edge cases. Tests instantiate `KnowledgeBaseWriter` directly against a `SailRepository(MemoryStore)` — no Spring context, no network.

```bash
# Run only PBT tests
mvn test -Dtest="*PropertyTest"

# Run a single property test
mvn test -Dtest="StateChangedPropertyTest"
```

jqwik stores its seed database in `.jqwik-database` at the module root. To reproduce a specific failing seed:

```bash
mvn test -Dtest="IdempotencyPropertyTest" -Djqwik.seeds.fixed=<seed>
```

### Integration tests

Integration tests use `@SpringBootTest(webEnvironment = NONE)` with `@ActiveProfiles("test")`. The `application-test.yml` profile wires WireMock ports into the Spring context automatically.

```bash
# Run only integration tests
mvn test -Dtest="*IT"
```

### Running a specific test class

```bash
mvn test -Dtest="InversePropertyTest,SensorIngestionGrpcServiceIT"
```

---

## gRPC API

The proto source lives in `../../schema/metis.proto`. The generated service is `SensorIngestionService` with a single RPC:

```protobuf
service SensorIngestionService {
  rpc IngestBatch (SensorBatch) returns (IngestResponse);
}
```

**`SensorBatch`** — one or more events plus a correlation ID (max 128 chars).

**`SensorEvent`** — a `oneof` carrying exactly one of:
- `EntityDiscoveredEvent` — new entity with type, ID, name, and optional properties
- `RelationshipAssertedEvent` — subject/predicate/object triple
- `StateChangedEvent` — new lifecycle state for an entity
- `EntityDeletedEvent` — remove all triples for an entity
- `MetricMetadataRegisteredEvent` — metric endpoint URL and name (no numeric values)

**`IngestResponse`** fields:

| Field | Type | Description |
|---|---|---|
| `accepted` | bool | `true` if the batch was processed without a system-level failure |
| `correlation_id` | string | Echoes the request correlation ID |
| `processed_count` | int32 | Number of events successfully written to GraphDB |
| `message` | string | Human-readable summary or error description |

**gRPC status codes:**

| Scenario | Status |
|---|---|
| Batch processed (even if some events failed validation) | `OK` |
| GraphDB unavailable | `UNAVAILABLE` |
| Unexpected internal error | `INTERNAL` |
| `correlation_id` > 128 chars | `OK` with `accepted = false` |

---

## Kubernetes Sensor Layer

Metis includes a built-in sensor layer that watches a Kubernetes cluster and automatically feeds events into the ingestion pipeline. It runs inside the same JVM — no separate process or network hop.

### How it works

1. On startup, `SensorOrchestrator` starts all `KubernetesSensor` beans (if `metis.sensor.enabled=true`).
2. Each sensor creates a Fabric8 `SharedIndexInformer` for its resource type. Informers reconnect automatically on failure.
3. Callbacks translate Kubernetes events into `SensorEvent` protos and hand them to `SensorEventPublisher`.
4. `SensorEventPublisher` buffers events and flushes them as a `SensorBatch` directly to `SensorEventProcessor` — in-process, no gRPC overhead.

### Built-in sensors

| Sensor | Watches | Emits |
|---|---|---|
| `PodSensor` | Pods | `EntityDiscovered`, `StateChanged`, `EntityDeleted` |
| `ServiceSensor` | Services | `EntityDiscovered`, `EntityDeleted` |
| `NodeSensor` | Nodes (cluster-scoped) | `EntityDiscovered`, `EntityDeleted` |
| `BindingSensor` | Pods + Services | `RelationshipAsserted` (pod↔service `cnee:contains`, pod↔node `cnee:isHostedOn`) |

### Configuration

```yaml
metis:
  sensor:
    enabled: true
    namespaces: []          # empty = all namespaces; e.g. [default, production]
    batch-size: 50          # max events per flush
    flush-interval-ms: 500  # flush every 500ms even if batch isn't full
```

Kubernetes credentials are resolved automatically by Fabric8:
1. In-cluster service account (`/var/run/secrets/kubernetes.io/serviceaccount`)
2. `KUBECONFIG` environment variable
3. `~/.kube/config`

When `metis.sensor.enabled=false` (the default in the test profile), no Kubernetes client is created — Metis runs fine without cluster access.

### Adding a new sensor

1. Create a class in `com.kubiki.metis.sensor.kubernetes` (or any sub-package).
2. Implement `KubernetesSensor` (or extend `AbstractNamespacedSensor` for namespaced resources).
3. Annotate with `@Component` and `@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true")`.
4. Inject `SensorEventPublisher` and call `publisher.publish(...)` from your informer callbacks.

That's it — `SensorOrchestrator` picks it up automatically. No other wiring needed.

```java
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true")
public class DeploymentSensor extends AbstractNamespacedSensor {

    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;

    public DeploymentSensor(KubernetesClient client, MetisProperties props,
                            SensorEventPublisher publisher, IriFactory iriFactory) {
        super(client, props);
        this.publisher = publisher;
        this.iriFactory = iriFactory;
    }

    @Override public String name() { return "DeploymentSensor"; }

    @Override
    protected SharedIndexInformer<Deployment> createInformer(KubernetesClient client, String namespace) {
        var op = namespace != null ? client.apps().deployments().inNamespace(namespace)
                                   : client.apps().deployments().inAnyNamespace();
        return op.inform(new ResourceEventHandler<>() {
            @Override public void onAdd(Deployment d) { /* publish EntityDiscoveredEvent */ }
            @Override public void onUpdate(Deployment o, Deployment n) { }
            @Override public void onDelete(Deployment d, boolean u) { /* publish EntityDeletedEvent */ }
        });
    }
}
```

---

## Package Structure

```
com.kubiki.metis
├── MetisApplication.java
├── config/
│   ├── MetisProperties.java        # @ConfigurationProperties(prefix = "metis")
│   ├── GraphDBConfig.java          # RDF4J HTTPRepository bean
│   ├── GrpcClientConfig.java       # Palamedes ManagedChannel + blocking stub beans
│   └── KubernetesClientConfig.java # Fabric8 KubernetesClient bean (conditional on sensor.enabled)
├── grpc/
│   └── SensorIngestionGrpcService.java   # @GrpcService entry point
├── ingestion/
│   ├── SensorEventProcessor.java   # Dispatches events to handlers, aggregates ProcessResult
│   ├── handler/
│   │   ├── SensorEventHandler.java         # Interface
│   │   ├── EntityDiscoveredHandler.java
│   │   ├── RelationshipAssertedHandler.java
│   │   ├── StateChangedHandler.java
│   │   ├── EntityDeletedHandler.java
│   │   └── MetricMetadataRegisteredHandler.java
│   └── model/
│       ├── HandlerResult.java      # Per-event result (success/failure/graphDbFailure)
│       └── ProcessResult.java      # Aggregated batch result
├── knowledge/
│   ├── KnowledgeBaseWriter.java    # Builds and executes SPARQL updates
│   ├── KnowledgeBaseException.java # Checked exception for GraphDB failures
│   └── OntologyRegistry.java       # CNEEOnt IRI factory
├── notification/
│   └── PalamedesNotifier.java      # Fire-and-forget TriggerUpdate gRPC call
└── sensor/
    ├── KubernetesSensor.java        # Extension point interface — implement to add a sensor
    ├── SensorOrchestrator.java      # Starts/stops all KubernetesSensor beans on app lifecycle
    ├── SensorEventPublisher.java    # Buffers events and flushes batches in-process
    ├── IriFactory.java              # CNEEOnt IRI construction from k8s resource names
    └── kubernetes/
        ├── AbstractNamespacedSensor.java  # Base class for namespace-aware sensors
        ├── PodSensor.java                 # Pods → EntityDiscovered/StateChanged/EntityDeleted
        ├── ServiceSensor.java             # Services → EntityDiscovered/EntityDeleted
        ├── NodeSensor.java                # Nodes → EntityDiscovered/EntityDeleted
        └── BindingSensor.java             # pod↔service contains, pod↔node isHostedOn
```
