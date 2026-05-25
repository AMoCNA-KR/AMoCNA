# Metis Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the `metis` module to use `daedalus` for SPARQL generation and a shared `SparqlClient` from the `common` module.

**Architecture:** 
1. Move `SparqlClient` from `palamedes` to `common` to unify SPARQL execution.
2. Introduce `daedalus` to `metis` for template-based SPARQL generation.
3. Refactor `KnowledgeBaseWriter` in `metis` to delegate query construction to a `daedalus` repository.

**Tech Stack:** Java, Spring Boot, RDF4J, GraphDB, Daedalus.

---

### Task 1: Move SparqlClient to Common Module

**Files:**
- Create: `apps/core/common/src/main/java/com/kubiki/common/knowledge/SparqlClient.java`
- Modify: `apps/core/palamedes/src/main/java/com/kubiki/palamedes/knowledge/StateRepository.java`
- Modify: `apps/core/palamedes/src/main/java/com/kubiki/palamedes/knowledge/SparqlClient.java` (DELETE)
- Modify: `apps/core/palamedes/src/main/java/com/kubiki/palamedes/knowledge/GraphDBGateway.java` (Update import)
- Modify: `apps/core/palamedes/src/main/java/com/kubiki/palamedes/reasoner/RcaEngine.java` (Update import)
- Modify: `apps/core/palamedes/src/main/java/com/kubiki/palamedes/analyzer/AnomalyAgent.java` (Update import)

- [ ] **Step 1: Create SparqlClient in common**

```java
package com.kubiki.common.knowledge;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@Component
public class SparqlClient {
    private static final Logger log = LoggerFactory.getLogger(SparqlClient.class);
    private final Repository repository;

    public SparqlClient(Repository repository) {
        this.repository = repository;
    }

    public <T> T executeQuery(String sparql, Function<Stream<BindingSet>, T> streamProcessor) {
        log.debug("Executing SPARQL:\n{}", sparql);
        try (RepositoryConnection connection = repository.getConnection()) {
            try (TupleQueryResult result = connection.prepareTupleQuery(sparql).evaluate()) {
                List<BindingSet> list = result.stream().toList();
                log.debug("Query returned {} rows", list.size());
                return streamProcessor.apply(list.stream());
            }
        }
    }

    public boolean executeUpdateWithSuccess(String sparql) {
        log.debug("Executing SPARQL UPDATE:\n{}", sparql);
        try (RepositoryConnection connection = repository.getConnection()) {
            connection.begin();
            connection.prepareUpdate(sparql).execute();
            connection.commit();
            return true;
        }
    }

    public boolean executeBooleanQuery(String sparql) {
        log.debug("Executing BOOLEAN SPARQL:\n{}", sparql);
        try (RepositoryConnection connection = repository.getConnection()) {
            return connection.prepareBooleanQuery(sparql).evaluate();
        }
    }

    public void executeWithConnection(Consumer<RepositoryConnection> action) {
        try (RepositoryConnection connection = repository.getConnection()) {
            action.accept(connection);
        }
    }

    public <T> T executeWithConnection(Function<RepositoryConnection, T> action) {
        try (RepositoryConnection connection = repository.getConnection()) {
            return action.apply(connection);
        }
    }
}
```

- [ ] **Step 2: Delete SparqlClient from palamedes**

Run: `rm apps/core/palamedes/src/main/java/com/kubiki/palamedes/knowledge/SparqlClient.java`

- [ ] **Step 3: Update imports in palamedes**

Replace `import com.kubiki.palamedes.knowledge.SparqlClient;` with `import com.kubiki.common.knowledge.SparqlClient;` in:
* `apps/core/palamedes/src/main/java/com/kubiki/palamedes/knowledge/StateRepository.java`
* `apps/core/palamedes/src/main/java/com/kubiki/palamedes/knowledge/GraphDBGateway.java`
* `apps/core/palamedes/src/main/java/com/kubiki/palamedes/reasoner/RcaEngine.java`
* `apps/core/palamedes/src/main/java/com/kubiki/palamedes/analyzer/AnomalyAgent.java`

- [ ] **Step 4: Verify palamedes build**

Run: `mvn clean install -pl apps/core/common,apps/core/palamedes -DskipTests`

- [ ] **Step 5: Commit changes**

```bash
git add apps/core/common apps/core/palamedes
git commit -m "refactor: move SparqlClient to common module"
```

### Task 2: Add Daedalus to Metis and Create Repository

**Files:**
- Modify: `apps/core/metis/pom.xml`
- Create: `apps/core/metis/src/main/java/com/kubiki/metis/knowledge/MetisDaedalusRepository.java`
- Create: `apps/core/metis/src/main/java/com/kubiki/metis/config/DaedalusInitializer.java`

- [ ] **Step 1: Add daedalus dependency to metis/pom.xml**

```xml
        <dependency>
            <groupId>com.kubiki</groupId>
            <artifactId>daedalus</artifactId>
        </dependency>
```

- [ ] **Step 2: Create MetisDaedalusRepository interface**

```java
package com.kubiki.metis.knowledge;

import com.kubiki.daedalus.annotation.*;

@DaedalusRepository
public interface MetisDaedalusRepository {

    @Template(resource = "sparql/insert-entity.sparql")
    String insertEntity(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri,
            @Type(TemplateType.IRI) @Bind("IRI::ontologyType") String ontologyType,
            @Bind("resourceId") String resourceId,
            @Bind("resourceName") String resourceName,
            @Bind("triples") String triples
    );

    @Template(resource = "sparql/assert-relationship.sparql")
    String assertRelationship(
            @Type(TemplateType.IRI) @Bind("IRI::subjectIri") String subjectIri,
            @Bind("predicate") String predicate,
            @Type(TemplateType.IRI) @Bind("IRI::objectIri") String objectIri
    );

    @Template(resource = "sparql/assert-relationship-pair.sparql")
    String assertRelationshipPair(
            @Type(TemplateType.IRI) @Bind("IRI::subjectIri") String subjectIri,
            @Bind("predicate") String predicate,
            @Type(TemplateType.IRI) @Bind("IRI::objectIri") String objectIri,
            @Bind("inversePredicate") String inversePredicate
    );

    @Template(resource = "sparql/change-state.sparql")
    String changeState(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri,
            @Type(TemplateType.IRI) @Bind("IRI::newStateIri") String newStateIri
    );

    @Template(resource = "sparql/delete-entity.sparql")
    String deleteEntity(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri
    );

    @Template(resource = "sparql/register-metric.sparql")
    String registerMetricMetadata(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri,
            @Bind("endpointUrl") String endpointUrl,
            @Type(TemplateType.IRI) @Bind("IRI::metricIri") String metricIri,
            @Bind("metricName") String metricName
    );
}
```

- [ ] **Step 3: Create DaedalusInitializer in metis**

```java
package com.kubiki.metis.config;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.metis.sensor.IriFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DaedalusInitializer {

    private final GlobalTemplateContext ctx;
    private final AmocnaCommonProperties commonProperties;
    private final IriFactory iriFactory;

    @PostConstruct
    public void init() {
        log.info("Initializing Daedalus global variables for Metis...");

        String prefixes = String.format(
                """
                        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                        PREFIX owl: <http://www.w3.org/2002/07/owl#>
                        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                        PREFIX cnee: <%s>
                        """,
                iriFactory.getCneeNamespace()
        );
        ctx.set("SPARQL_PREFIXES", prefixes);

        log.info("Finished initializing Daedalus global variables for Metis");
    }
}
```

- [ ] **Step 4: Commit changes**

```bash
git add apps/core/metis
git commit -m "feat(metis): add daedalus and repository interface"
```

### Task 3: Create SPARQL Templates for Metis

**Files:**
- Create: `apps/core/metis/src/main/resources/sparql/insert-entity.sparql`
- Create: `apps/core/metis/src/main/resources/sparql/assert-relationship.sparql`
- Create: `apps/core/metis/src/main/resources/sparql/assert-relationship-pair.sparql`
- Create: `apps/core/metis/src/main/resources/sparql/change-state.sparql`
- Create: `apps/core/metis/src/main/resources/sparql/delete-entity.sparql`
- Create: `apps/core/metis/src/main/resources/sparql/register-metric.sparql`

- [ ] **Step 1: Create insert-entity.sparql**

```sparql
[[SPARQL_PREFIXES]]
INSERT {
  <[[IRI::resourceIri]]> rdf:type <[[IRI::ontologyType]]> ;
    cnee:resourceID "[[resourceId]]"^^xsd:string ;
    cnee:resourceName "[[resourceName]]"^^xsd:string .
  [[triples]]
}
WHERE {
  FILTER NOT EXISTS { <[[IRI::resourceIri]]> rdf:type <[[IRI::ontologyType]]> }
}
```

- [ ] **Step 2: Create assert-relationship.sparql**

```sparql
[[SPARQL_PREFIXES]]
INSERT { <[[IRI::subjectIri]]> cnee:[[predicate]] <[[IRI::objectIri]]> . }
WHERE { FILTER NOT EXISTS { <[[IRI::subjectIri]]> cnee:[[predicate]] <[[IRI::objectIri]]> } }
```

- [ ] **Step 3: Create assert-relationship-pair.sparql**

```sparql
[[SPARQL_PREFIXES]]
INSERT {
  <[[IRI::subjectIri]]> cnee:[[predicate]] <[[IRI::objectIri]]> .
  <[[IRI::objectIri]]> cnee:[[inversePredicate]] <[[IRI::subjectIri]]> .
}
WHERE {
  FILTER NOT EXISTS { <[[IRI::subjectIri]]> cnee:[[predicate]] <[[IRI::objectIri]]> }
}
```

- [ ] **Step 4: Create change-state.sparql**

```sparql
[[SPARQL_PREFIXES]]
DELETE {
  <[[IRI::resourceIri]]> cnee:hasState ?oldState .
}
INSERT {
  <[[IRI::resourceIri]]> cnee:hasState <[[IRI::newStateIri]]> .
}
WHERE {
  OPTIONAL { <[[IRI::resourceIri]]> cnee:hasState ?oldState }
}
```

- [ ] **Step 5: Create delete-entity.sparql**

```sparql
[[SPARQL_PREFIXES]]
DELETE {
  <[[IRI::resourceIri]]> ?p ?o .
  ?s ?p2 <[[IRI::resourceIri]]> .
}
WHERE {
  { <[[IRI::resourceIri]]> ?p ?o }
  UNION
  { ?s ?p2 <[[IRI::resourceIri]]> }
}
```

- [ ] **Step 6: Create register-metric.sparql**

```sparql
[[SPARQL_PREFIXES]]
INSERT {
  <[[IRI::resourceIri]]>  cnee:metricsEndpoint "[[endpointUrl]]"^^xsd:anyURI .
  <[[IRI::metricIri]]>  rdf:type cnee:Metric .
  <[[IRI::metricIri]]>  cnee:resourceName "[[metricName]]"^^xsd:string .
  <[[IRI::resourceIri]]>  cnee:emitsTelemetry <[[IRI::metricIri]]> .
}
WHERE {
  FILTER NOT EXISTS { <[[IRI::resourceIri]]> cnee:emitsTelemetry <[[IRI::metricIri]]> }
}
```

- [ ] **Step 7: Commit changes**

```bash
git add apps/core/metis/src/main/resources/sparql
git commit -m "feat(metis): add SPARQL templates"
```

### Task 4: Refactor KnowledgeBaseWriter

**Files:**
- Modify: `apps/core/metis/src/main/java/com/kubiki/metis/knowledge/KnowledgeBaseWriter.java`

- [ ] **Step 1: Refactor KnowledgeBaseWriter to use Repository and SparqlClient**

```java
package com.kubiki.metis.knowledge;

import com.kubiki.common.knowledge.SparqlClient;
import com.kubiki.metis.grpc.EntityDeletedEvent;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.MetricMetadataRegisteredEvent;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.grpc.StateChangedEvent;
import com.kubiki.metis.sensor.IriFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class KnowledgeBaseWriter {

    private final SparqlClient sparqlClient;
    private final MetisDaedalusRepository repository;
    private final String cneeNamespace;

    public KnowledgeBaseWriter(SparqlClient sparqlClient, MetisDaedalusRepository repository, IriFactory iriFactory) {
        this.sparqlClient = sparqlClient;
        this.repository = repository;
        this.cneeNamespace = iriFactory.getCneeNamespace();
    }

    public void insertEntity(EntityDiscoveredEvent event) throws KnowledgeBaseException {
        String resourceIri  = event.getResourceIri();
        String ontologyType = event.getOntologyType();
        String resourceId   = event.getResourceId();
        String resourceName = event.getResourceName();

        if (!isValidCneeIri(ontologyType)) {
            throw new KnowledgeBaseException(
                    "Rejected: ontology_type '" + ontologyType +
                    "' does not start with CNEEOnt namespace '" + cneeNamespace + "'");
        }

        StringBuilder triples = new StringBuilder();
        for (Map.Entry<String, String> entry : event.getPropertiesMap().entrySet()) {
            String key   = entry.getKey();
            String value = entry.getValue();

            if (key == null || key.isEmpty() || value == null) continue;
            if (!key.startsWith("http://") && !key.startsWith("https://")) continue;

            triples.append("    <").append(key).append("> \"")
                    .append(escapeLiteral(value)).append("\"^^xsd:string ;\n");
        }
        if (triples.length() > 2) {
            triples.setLength(triples.length() - 2);
            triples.append(" .");
        }

        String sparql = repository.insertEntity(resourceIri, ontologyType, escapeLiteral(resourceId), escapeLiteral(resourceName), triples.toString());
        executeUpdate(sparql);
    }

    public void assertRelationship(RelationshipAssertedEvent event) throws KnowledgeBaseException {
        String subjectIri = event.getSubjectIri();
        String predicate  = event.getPredicate();
        String objectIri  = event.getObjectIri();

        validateIri(subjectIri, "subject_iri");
        validateIri(objectIri, "object_iri");
        if (!isValidCneeIri(predicate)) {
            throw new KnowledgeBaseException("predicate does not start with CNEEOnt namespace: " + predicate);
        }

        String inverseLocalName = inverseLocalName(predicate);
        String predicateLocalName = predicate.substring(cneeNamespace.length());
        String sparql;

        if (inverseLocalName != null) {
            sparql = repository.assertRelationshipPair(subjectIri, predicateLocalName, objectIri, inverseLocalName);
        } else {
            sparql = repository.assertRelationship(subjectIri, predicateLocalName, objectIri);
        }

        executeUpdate(sparql);
    }

    public void changeState(StateChangedEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        String newStateIri = event.getNewStateIri();

        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException("changeState: resource_iri must not be blank");
        }
        if (newStateIri == null || newStateIri.isBlank()) {
            throw new KnowledgeBaseException("changeState: new_state_iri must not be blank");
        }
        if (!isValidCneeIri(newStateIri)) {
            throw new KnowledgeBaseException(
                    "changeState: new_state_iri must start with CNEEOnt namespace, got: " + newStateIri);
        }

        String sparql = repository.changeState(resourceIri, newStateIri);
        executeUpdate(sparql);
    }

    public void deleteEntity(EntityDeletedEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException("deleteEntity: resource_iri must not be blank");
        }

        String sparql = repository.deleteEntity(resourceIri);
        executeUpdate(sparql);
    }

    public void registerMetricMetadata(MetricMetadataRegisteredEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        String endpointUrl = event.getEndpointUrl();
        String metricName  = event.getMetricName();

        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException("registerMetricMetadata: resource_iri must not be blank");
        }

        try {
            java.net.URI uri = new java.net.URI(endpointUrl);
            if (!uri.isAbsolute()) {
                throw new KnowledgeBaseException(
                        "registerMetricMetadata: endpoint_url is not an absolute URI: " + endpointUrl);
            }
        } catch (Exception e) {
            throw new KnowledgeBaseException(
                    "registerMetricMetadata: endpoint_url is not a valid URI: " + endpointUrl, e);
        }

        String resourceLocalName = localNameOf(resourceIri);
        if (resourceLocalName == null) {
            throw new KnowledgeBaseException(
                    "registerMetricMetadata: cannot derive local name from resource_iri: " + resourceIri);
        }
        String metricIri = cneeNamespace + encodeFragment(resourceLocalName) + "_" + encodeFragment(metricName);

        String sparql = repository.registerMetricMetadata(resourceIri, endpointUrl, metricIri, escapeLiteral(metricName));
        executeUpdate(sparql);
    }

    private void validateIri(String iri, String field) throws KnowledgeBaseException {
        if (iri == null || (!iri.startsWith("http://") && !iri.startsWith("https://"))) {
            throw new KnowledgeBaseException(field + " is not an absolute IRI: " + iri);
        }
    }

    private String inverseLocalName(String predicate) {
        String localName = predicate.substring(cneeNamespace.length());
        return switch (localName) {
            case CneeOntology.PROP_CONTAINS          -> CneeOntology.PROP_IS_PART_OF;
            case CneeOntology.PROP_IS_PART_OF        -> CneeOntology.PROP_CONTAINS;
            case CneeOntology.PROP_HOSTS             -> CneeOntology.PROP_IS_HOSTED_ON;
            case CneeOntology.PROP_IS_HOSTED_ON      -> CneeOntology.PROP_HOSTS;
            case CneeOntology.PROP_COMMUNICATES_WITH -> CneeOntology.PROP_COMMUNICATES_WITH;
            default                                  -> null;
        };
    }

    protected void executeUpdate(String sparql) throws KnowledgeBaseException {
        try {
            sparqlClient.executeUpdateWithSuccess(sparql);
        } catch (Exception e) {
            log.error("SPARQL update failed. Query: {}", sparql, e);
            throw new KnowledgeBaseException("SPARQL update failed: " + e.getMessage(), e);
        }
    }

    private boolean isValidCneeIri(String iri) {
        return iri != null && iri.startsWith(cneeNamespace);
    }

    private String localNameOf(String iri) {
        if (iri == null || iri.isBlank()) return null;
        int hash  = iri.lastIndexOf('#');
        int slash = iri.lastIndexOf('/');
        int idx   = Math.max(hash, slash);
        if (idx < 0 || idx >= iri.length() - 1) return null;
        return iri.substring(idx + 1);
    }

    private String escapeLiteral(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }

    private String encodeFragment(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
```

- [ ] **Step 2: Verify metis build**

Run: `mvn clean install -pl apps/core/metis -DskipTests`

- [ ] **Step 3: Commit changes**

```bash
git add apps/core/metis/src/main/java/com/kubiki/metis/knowledge/KnowledgeBaseWriter.java
git commit -m "refactor(metis): use Daedalus repository and SparqlClient in KnowledgeBaseWriter"
```

### Task 5: Cleanup and Final Verification

- [ ] **Step 1: Verify entire project**

Run: `mvn clean install -pl apps/core/common,apps/core/metis,apps/core/palamedes -DskipTests`

- [ ] **Step 2: Run Metis tests (if any)**

Run: `mvn test -pl apps/core/metis`

- [ ] **Step 3: Run Palamedes tests**

Run: `mvn test -pl apps/core/palamedes`
