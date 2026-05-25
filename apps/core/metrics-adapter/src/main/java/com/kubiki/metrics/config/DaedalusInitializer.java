package com.kubiki.metrics.config;

import com.kubiki.daedalus.context.GlobalTemplateContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DaedalusInitializer {
    private final GlobalTemplateContext ctx;

    @Value("${ontology.cnee-prefix:cnee}")
    private String cneePrefix;

    @Value("${ontology.resources-namespace:http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/}")
    private String cneeNamespace;

    @PostConstruct
    public void init() {
        ctx.set("SPARQL_PREFIXES", String.format("PREFIX %s: <%s>", cneePrefix, cneeNamespace));
        ctx.set("PREFIX::CNEE_PREFIX", cneePrefix);
    }
}
