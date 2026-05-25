# Metis Refactoring Design

Refactor `metis` to use `daedalus` and `common` module, following the pattern from `palamedes` and `themis`.

## Architecture Changes

1.  **Common Module**:
    *   Move `SparqlClient` from `palamedes` to `common`.
    *   `common` module will now provide a standard way to execute SPARQL queries.

2.  **Metis Module**:
    *   Add `daedalus` dependency.
    *   Introduce `MetisDaedalusRepository` interface annotated with `@DaedalusRepository`.
    *   Move SPARQL query templates to `src/main/resources/sparql/`.
    *   Introduce `DaedalusInitializer` to configure global SPARQL prefixes.
    *   Refactor `KnowledgeBaseWriter` to use the repository and `SparqlClient`.

## Implementation Details

### 1. Move SparqlClient
*   Source: `apps/core/palamedes/src/main/java/com/kubiki/palamedes/knowledge/SparqlClient.java`
*   Destination: `apps/core/common/src/main/java/com/kubiki/common/knowledge/SparqlClient.java` (adjust package name)
*   Update `palamedes` imports.

### 2. Metis Repository & Templates
*   `MetisDaedalusRepository` methods will correspond to the operations in `KnowledgeBaseWriter`:
    *   `insertEntity`
    *   `assertRelationship`
    *   `changeState`
    *   `deleteEntity`
    *   `registerMetricMetadata`
*   Templates will use Daedalus binding syntax (e.g., `@Bind("IRI::resourceIri")`).

### 3. KnowledgeBaseWriter Refactoring
*   Remove manual string concatenation for SPARQL.
*   Inject `MetisDaedalusRepository` and `SparqlClient`.
*   Delegate query generation to the repository and execution to the client.

## Success Criteria
*   `metis` builds successfully.
*   Existing tests in `metis` pass (if any).
*   `palamedes` still builds and tests pass.
