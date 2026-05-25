package com.kubiki.themis.config;

import com.kubiki.daedalus.context.GlobalTemplateContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DaedalusInitializer {
    private final GlobalTemplateContext ctx;

    @Value("${ontology.moam-prefix:moam}")
    private String moamPrefix;

    @Value("${ontology.actions-namespace:http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#}")
    private String moamNamespace;

    @PostConstruct
    public void init() {
        ctx.set("SPARQL_PREFIXES", String.format("PREFIX %s: <%s>", moamPrefix, moamNamespace));
        ctx.set("PREFIX::MOAM_PREFIX", moamPrefix);
    }
}
