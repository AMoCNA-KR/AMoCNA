package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.templating.SparqlTemplateEngine;
import com.kubiki.palamedes.templating.types.TemplatingType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedList;

@Component
@RequiredArgsConstructor
public class SparqlQueryBuilder {

    private final SparqlLoader sparqlLoader;
    private final SparqlTemplateEngine sparqlTemplateEngine;

    public QueryBuilder builder() {
        return new QueryBuilder();
    }

    public class QueryBuilder {
        private String templateName;
        private final LinkedList<TemplatingType<?>> variables = new LinkedList<>();

        public QueryBuilder template(String templateName) {
            this.templateName = templateName;
            return this;
        }

        public QueryBuilder variable(TemplatingType<?> variable) {
            variables.add(variable);
            return this;
        }

        public String build() {
            String rawTemplate = sparqlLoader.loadRaw(templateName);
            return sparqlTemplateEngine.populateTemplate(rawTemplate, variables);
        }

    }
}
