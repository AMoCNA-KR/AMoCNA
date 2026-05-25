# Daedalus Execution Refactoring Plan

**Goal:** Transform `daedalus` from a pure templating library into a full-featured SPARQL repository framework that handles both templating and execution.

## Task 1: Enhance Daedalus Core

- [ ] **Step 1: Update `daedalus/pom.xml`**
  Add RDF4J repository and query dependencies.
- [ ] **Step 2: Create execution annotations**
  Create `@SparqlQuery` and `@SparqlUpdate` in `com.kubiki.daedalus.annotation`.
- [ ] **Step 3: Modify `DaedalusInvocationHandler`**
  - Inject `org.eclipse.rdf4j.repository.Repository`.
  - Add logic to detect `@SparqlQuery` / `@SparqlUpdate`.
  - Implement execution logic based on return type (e.g., `void` for updates, `List<BindingSet>` or mapped objects for queries).
- [ ] **Step 4: Update `DaedalusAutoConfiguration` and `DaedalusBeanRegistrar`**
  - Ensure `Repository` bean can be injected into the handlers.

## Task 2: Refactor Metis

- [ ] **Step 1: Update `MetisDaedalusRepository`**
  Change return types from `String` to `void` for updates.
- [ ] **Step 2: Refactor `KnowledgeBaseWriter`**
  Remove `SparqlClient` dependency and call repository methods directly.

## Task 3: Refactor Palamedes

- [ ] **Step 1: Update `SparqlRepository`**
  Change return types to `void`, `List<BindingSet>`, or domain objects.
- [ ] **Step 2: Refactor `StateRepository` and others**
  Call `SparqlRepository` directly for execution.

## Task 4: Refactor Themis

- [ ] **Step 1: Update `themis/SparqlRepository`**
  Use the new execution approach.

## Task 5: Cleanup

- [ ] **Step 1: Remove `SparqlClient` from `common`** (if no longer needed).
- [ ] **Step 2: Verify all modules.**
