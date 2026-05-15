package com.kubiki.palamedes.knowledge;

import org.eclipse.rdf4j.model.IRI;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SparqlQueryBuilder {

    private final SparqlLoader sparqlLoader;
    private final OntologyRegistry ontologyRegistry;

    public SparqlQueryBuilder(SparqlLoader sparqlLoader, OntologyRegistry ontologyRegistry) {
        this.sparqlLoader = sparqlLoader;
        this.ontologyRegistry = ontologyRegistry;
    }

    public QueryBuilder builder() {
        return new QueryBuilder();
    }

    public class QueryBuilder {
        private String templateName;
        private final Map<String, Object> variables = new HashMap<>();

        public QueryBuilder template(String templateName) {
            this.templateName = templateName;
            return this;
        }

        public QueryBuilder variable(String name, Object value) {
            variables.put(name, value);
            return this;
        }

        public String build() {
            String rawTemplate = sparqlLoader.loadRaw(templateName);
            StringBuilder sb = new StringBuilder();
            
            // Standard Industrial Prefixes
            appendPrefix(sb, "moam", ontologyRegistry.getMoamNamespace());
            appendPrefix(sb, "cnee", ontologyRegistry.getCneeNamespace());
            appendPrefix(sb, "bridge", ontologyRegistry.getBridgeNamespace());
            appendPrefix(sb, "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
            appendPrefix(sb, "rdfs", "http://www.w3.org/2000/01/rdf-schema#");
            appendPrefix(sb, "owl", "http://www.w3.org/2002/07/owl#");
            
            String processed = rawTemplate;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                processed = processed.replace("${" + entry.getKey() + "}", formatValue(entry.getValue()));
            }
            
            sb.append(processed);
            return sb.toString();
        }

        private void appendPrefix(StringBuilder sb, String prefix, String namespace) {
            sb.append("PREFIX ").append(prefix).append(": <").append(namespace).append(">\n");
        }

        private String formatValue(Object value) {
            if (value instanceof IRI iri) {
                return "<" + iri.stringValue() + ">";
            }
            if (value instanceof String s) {
                return "\"" + escapeSparqlString(s) + "\"";
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            return "\"" + escapeSparqlString(String.valueOf(value)) + "\"";
        }

        private String escapeSparqlString(String value) {
            StringBuilder escaped = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\': escaped.append("\\\\"); break;
                    case '"': escaped.append("\\\""); break;
                    case '\n': escaped.append("\\n"); break;
                    case '\r': escaped.append("\\r"); break;
                    case '\t': escaped.append("\\t"); break;
                    case '\b': escaped.append("\\b"); break;
                    case '\f': escaped.append("\\f"); break;
                    default: escaped.append(c);
                }
            }
            return escaped.toString();
        }
    }
}
