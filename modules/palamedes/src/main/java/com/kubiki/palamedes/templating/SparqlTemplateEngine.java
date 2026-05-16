package com.kubiki.palamedes.templating;

import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.templating.types.*;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.kubiki.palamedes.templating.TemplatingConstants.*;

@Component
public class SparqlTemplateEngine {
    private final LinkedList<PrefixType> prefixes;
    private final LinkedList<TemplatingType<?>> templatedPrefixes;

    public SparqlTemplateEngine(PalamedesProperties properties) {
        this.templatedPrefixes = new LinkedList<>();

        this.templatedPrefixes.add(new PrefixType(ACTIONS_PREFIX_VARIABLE, properties.ontology().actionsPrefix()));
        this.templatedPrefixes.add(new PrefixType(RESOURCES_PREFIX_VARIABLE, properties.ontology().resourcesPrefix()));
        this.templatedPrefixes.add(new PrefixType(BRIDGE_PREFIX_VARIABLE, properties.ontology().bridgePrefix()));

        this.prefixes = new LinkedList<>();
        prefixes.add(new PrefixType(properties.ontology().actionsPrefix(), properties.ontology().actionsNamespace()));
        prefixes.add(new PrefixType(properties.ontology().resourcesPrefix(), properties.ontology().resourcesNamespace()));
        prefixes.add(new PrefixType(properties.ontology().bridgePrefix(), properties.ontology().bridgeNamespace()));
        prefixes.add(new PrefixType("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"));
        prefixes.add(new PrefixType("rdfs", "http://www.w3.org/2000/01/rdf-schema#"));
        prefixes.add(new PrefixType("owl", "http://www.w3.org/2002/07/owl#"));

    }


    public String populateTemplate(String rawTemplate, LinkedList<TemplatingType<?>> variables) {
        StringBuilder sb = new StringBuilder();

        populatePrefixes(sb);
        sb.append(rawTemplate);
        var prefixedTemplate = sb.toString();
        prefixedTemplate = substituteVariables(prefixedTemplate, templatedPrefixes);
        prefixedTemplate = substituteVariables(prefixedTemplate, variables);

        return prefixedTemplate;
    }

    private String substituteVariables(String template, LinkedList<TemplatingType<?>> variables) {
        for (var value : variables) {
            template = template.replace(BEGIN_OF_VARIABLE + value.prefix() + value.key() +  END_OF_VARIABLE, value.format());
        }
        return template;
    }


    private void populatePrefixes(StringBuilder sb) {
        for (var prefix: this.prefixes) {
            appendPrefix(sb, prefix);
        }
    }

    private void appendPrefix(StringBuilder sb, PrefixType pt) {
        sb.append(SPARQL_PREFIX).append(pt.key()).append(": ").append(BEGIN_OF_IRI_VARIABLE).append(pt.value()).append(END_OF_IRI_VARIABLE).append("\n");
    }
}
