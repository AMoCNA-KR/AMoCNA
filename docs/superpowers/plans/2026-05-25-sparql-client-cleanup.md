# SparqlClient Refactoring Completion Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the move of `SparqlClient` to `daedalus`, update all imports, ensure tests pass, and clean up `common` dependencies.

**Architecture:**
- `daedalus` module owns `SparqlClient` and core RDF4J/GraphDB connection dependencies.
- `common` module provides `OntologyRegistry` and lightweight RDF4J model types.
- `metis` and `palamedes` use `SparqlClient` from `daedalus`.

**Tech Stack:** Java 25, Spring Boot 4, RDF4J, GraphDB, JUnit 5, jqwik.

---

### Task 1: Update Imports in metis PBT Tests

**Files:**
- Modify: `apps/core/metis/src/test/java/com/kubiki/metis/pbt/EntityDiscoveredPropertyTest.java`

- [ ] **Step 1: Remove unused SparqlClient import in EntityDiscoveredPropertyTest.java.**

### Task 2: Update Imports and usage in palamedes tests

**Files:**
- Modify: `apps/core/palamedes/src/test/java/com/kubiki/palamedes/integration/PerformanceScaleIT.java`

- [ ] **Step 1: Update import in PerformanceScaleIT.java from `com.kubiki.common.knowledge.SparqlClient` to `com.kubiki.daedalus.knowledge.SparqlClient`.**

### Task 3: Dependency Cleanup in common and daedalus

**Files:**
- Modify: `apps/core/common/pom.xml`
- Modify: `apps/core/daedalus/pom.xml`
- Modify: `pom.xml`

- [ ] **Step 1: Add `rdf4j-model` to `dependencyManagement` in root `pom.xml`.**
- [ ] **Step 2: Remove `graphdb-runtime` from `apps/core/common/pom.xml` and add `rdf4j-model`.**
- [ ] **Step 3: Add `graphdb-runtime` to `apps/core/daedalus/pom.xml`.**

### Task 4: Final Verification

- [ ] **Step 1: Build all modules.**
- [ ] **Step 2: Run all tests in metis and palamedes.**
