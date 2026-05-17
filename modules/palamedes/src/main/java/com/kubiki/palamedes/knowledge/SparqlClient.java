package com.kubiki.palamedes.knowledge;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
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
        log.debug("Executing ATOMIC SPARQL UPDATE:\n{}", sparql);
        try (RepositoryConnection connection = repository.getConnection()) {
            connection.begin();
            String upper = sparql.toUpperCase();
            int deleteIndex = upper.indexOf("DELETE");
            int insertIndex = upper.indexOf("INSERT");
            int whereIndex = upper.lastIndexOf("WHERE");

            boolean success = true;
            if (whereIndex != -1) {
                int firstOpIndex = -1;
                if (deleteIndex != -1 && (insertIndex == -1 || deleteIndex < insertIndex)) {
                    firstOpIndex = deleteIndex;
                } else if (insertIndex != -1) {
                    firstOpIndex = insertIndex;
                }

                String prefixes = (firstOpIndex != -1) ? sparql.substring(0, firstOpIndex) : "";
                String whereClause = sparql.substring(whereIndex);
                String askQuery = prefixes + " ASK " + whereClause;
                log.debug("Checking precondition with ASK:\n{}", askQuery);
                success = connection.prepareBooleanQuery(askQuery).evaluate();
            }

            if (success) {
                connection.prepareUpdate(sparql).execute();
                log.debug("Update applied successfully");
            } else {
                log.warn("Update precondition failed (WHERE clause did not match)");
            }
            connection.commit();
            return success;
        }
    }

    public boolean executeBooleanQuery(String sparql) {
        log.debug("Executing BOOLEAN SPARQL:\n{}", sparql);
        try (RepositoryConnection connection = repository.getConnection()) {
            return connection.prepareBooleanQuery(sparql).evaluate();
        }
    }

    public void executeWithConnection(java.util.function.Consumer<RepositoryConnection> action) {
        try (RepositoryConnection connection = repository.getConnection()) {
            action.accept(connection);
        }
    }

    public <T> T executeWithConnection(java.util.function.Function<RepositoryConnection, T> action) {
        try (RepositoryConnection connection = repository.getConnection()) {
            return action.apply(connection);
        }
    }
}
