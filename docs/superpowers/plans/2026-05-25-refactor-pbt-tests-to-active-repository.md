# Refactor PBT Tests to Active Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor Property-Based Tests (PBT) in Metis and Palamedes to use active repository execution instead of SPARQL capturing.

**Architecture:** Use in-memory `SailRepository` with `RepositoryConnection` to verify side effects directly. Remove `CapturingKnowledgeBaseWriter` and update `DaedalusInvocationHandler` instantiation to include the repository.

**Tech Stack:** Java, RDF4J, jqwik, Daedalus (active repository), AssertJ.

---

### Task 1: Refactor EntityDiscoveredPropertyTest

**Files:**
- Modify: `apps/core/metis/src/test/java/com/kubiki/metis/pbt/EntityDiscoveredPropertyTest.java`

- [ ] **Step 1: Update imports and remove CapturingKnowledgeBaseWriter**
Remove `CapturingKnowledgeBaseWriter` class. Add RDF4J imports for querying.

- [ ] **Step 2: Update setup in property method**
Update `DaedalusInvocationHandler` to take 4 arguments (including `repo`). Update `KnowledgeBaseWriter` constructor (remove `sparqlClient`).

- [ ] **Step 3: Update assertions**
Use `RepositoryConnection` to count triples directly in the repository instead of analyzing SPARQL strings.

```java
try (RepositoryConnection conn = repo.getConnection()) {
    long rdfTypeCount = conn.getStatements(null, RDF.TYPE, null, false).stream()
            .filter(s -> s.getObject().stringValue().startsWith(CNEE_NAMESPACE))
            .count();
    assertThat(rdfTypeCount).isEqualTo(1);
    // ... other assertions
}
```

- [ ] **Step 4: Verify test passes**
Run: `mvn test -Dtest=EntityDiscoveredPropertyTest`

- [ ] **Step 5: Commit**

### Task 2: Refactor IdempotencyPropertyTest

**Files:**
- Modify: `apps/core/metis/src/test/java/com/kubiki/metis/pbt/IdempotencyPropertyTest.java`

- [ ] **Step 1: Update writerFor helper**
Update `DaedalusInvocationHandler` and `KnowledgeBaseWriter` instantiations. Remove `SparqlClient`.

- [ ] **Step 2: Verify test passes**
Run: `mvn test -Dtest=IdempotencyPropertyTest`

- [ ] **Step 3: Commit**

### Task 3: Refactor InversePropertyTest

**Files:**
- Modify: `apps/core/metis/src/test/java/com/kubiki/metis/pbt/InversePropertyTest.java`

- [ ] **Step 1: Update imports and remove CapturingKnowledgeBaseWriter**
- [ ] **Step 2: Update setup in property method**
- [ ] **Step 3: Update assertions to use RepositoryConnection**
- [ ] **Step 4: Verify test passes**
Run: `mvn test -Dtest=InversePropertyTest`
- [ ] **Step 5: Commit**

### Task 4: Refactor NoRawMetricValuesPropertyTest

**Files:**
- Modify: `apps/core/metis/src/test/java/com/kubiki/metis/pbt/NoRawMetricValuesPropertyTest.java`

- [ ] **Step 1: Update setup and remove capturing logic**
- [ ] **Step 2: Update assertions to use RepositoryConnection**
- [ ] **Step 3: Verify test passes**
Run: `mvn test -Dtest=NoRawMetricValuesPropertyTest`
- [ ] **Step 4: Commit**

### Task 5: Refactor StateChangedPropertyTest

**Files:**
- Modify: `apps/core/metis/src/test/java/com/kubiki/metis/pbt/StateChangedPropertyTest.java`

- [ ] **Step 1: Update setup and remove capturing logic**
- [ ] **Step 2: Update assertions to use RepositoryConnection**
- [ ] **Step 3: Verify test passes**
Run: `mvn test -Dtest=StateChangedPropertyTest`
- [ ] **Step 4: Commit**

### Task 6: Refactor SymmetricPropertyTest

**Files:**
- Modify: `apps/core/metis/src/test/java/com/kubiki/metis/pbt/SymmetricPropertyTest.java`

- [ ] **Step 1: Update setup and remove capturing logic**
- [ ] **Step 2: Update assertions to use RepositoryConnection**
- [ ] **Step 3: Verify test passes**
Run: `mvn test -Dtest=SymmetricPropertyTest`
- [ ] **Step 4: Commit**

### Task 7: Refactor Palamedes StateRepositoryTest

**Files:**
- Modify: `apps/core/palamedes/src/test/java/com/kubiki/palamedes/knowledge/StateRepositoryTest.java`

- [ ] **Step 1: Remove SparqlClient mock and update StateRepository instantiation**
- [ ] **Step 2: Use in-memory SailRepository for verification if possible, or keep Mockito but for SparqlRepository**
Actually, since `SparqlRepository` is an interface, it's easier to keep mocking it but remove the `SparqlClient`.

- [ ] **Step 3: Verify test passes**
Run: `mvn test -Dtest=StateRepositoryTest`
- [ ] **Step 4: Commit**
