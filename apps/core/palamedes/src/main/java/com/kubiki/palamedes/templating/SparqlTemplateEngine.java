package com.kubiki.palamedes.templating;

import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.templating.types.*;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.kubiki.palamedes.templating.TemplatingConstants.*;

@Component
public class SparqlTemplateEngine {

    private final LinkedList<PrefixType> prefixes;
    private final LinkedList<TemplatingType<?>> propertiesVariables;

    public SparqlTemplateEngine(PalamedesProperties properties) {
        this.propertiesVariables = new LinkedList<>();

        this.propertiesVariables.add(new PrefixType(ACTIONS_PREFIX_VARIABLE, properties.ontology().actionsPrefix()));
        this.propertiesVariables.add(new PrefixType(RESOURCES_PREFIX_VARIABLE, properties.ontology().resourcesPrefix()));
        this.propertiesVariables.add(new PrefixType(BRIDGE_PREFIX_VARIABLE, properties.ontology().bridgePrefix()));
        this.propertiesVariables.add(new IndividualType(STATE_INITIAL, properties.ontology().states().getOrDefault(PROPERTIES_INITIAL_STATE_NAME, DEFAULT_STATE_INITIAL)));
        this.propertiesVariables.add(new IndividualType(STATE_PLANNED, properties.ontology().states().getOrDefault(PROPERTIES_PLANNED_STATE_NAME, DEFAULT_STATE_PLANNED)));
        this.propertiesVariables.add(new IndividualType(STATE_VALIDATED, properties.ontology().states().getOrDefault(PROPERTIES_VALIDATED_STATE_NAME, DEFAULT_STATE_VALIDATED)));
        this.propertiesVariables.add(new IndividualType(STATE_IN_PROGRESS, properties.ontology().states().getOrDefault(PROPERTIES_IN_PROGRESS_STATE_NAME, DEFAULT_STATE_IN_PROGRESS)));
        this.propertiesVariables.add(new IndividualType(STATE_SUCCEEDED, properties.ontology().states().getOrDefault(PROPERTIES_SUCCEEDED_STATE_NAME, DEFAULT_STATE_SUCCEEDED)));
        this.propertiesVariables.add(new IndividualType(STATE_FAILED, properties.ontology().states().getOrDefault(PROPERTIES_FAILED_STATE_NAME, DEFAULT_STATE_FAILED)));
        this.propertiesVariables.add(new IndividualType(STATE_COMPENSATING, properties.ontology().states().getOrDefault(PROPERTIES_COMPENSATING_STATE_NAME, DEFAULT_STATE_COMPENSATING)));

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
        prefixedTemplate = substituteVariables(prefixedTemplate, propertiesVariables);
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
