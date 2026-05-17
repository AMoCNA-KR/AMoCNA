package com.kubiki.palamedes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.spring.DaedalusAutoConfiguration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static com.kubiki.palamedes.templating.TemplatingConstants.*;

@Configuration
@Import(DaedalusAutoConfiguration.class)
public class BeanConfig {

    @Bean
    public ObjectMapper getObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CommandLineRunner initDaedalus(GlobalTemplateContext ctx, PalamedesProperties properties) {
        return args -> {
            ctx.set("PREFIX::" + ACTIONS_PREFIX_VARIABLE, properties.ontology().actionsPrefix());
            ctx.set("PREFIX::" + RESOURCES_PREFIX_VARIABLE, properties.ontology().resourcesPrefix());
            ctx.set("PREFIX::" + BRIDGE_PREFIX_VARIABLE, properties.ontology().bridgePrefix());
            
            ctx.set("INDIVIDUAL::" + STATE_INITIAL, properties.ontology().states().getOrDefault(PROPERTIES_INITIAL_STATE_NAME, DEFAULT_STATE_INITIAL));
            ctx.set("INDIVIDUAL::" + STATE_PLANNED, properties.ontology().states().getOrDefault(PROPERTIES_PLANNED_STATE_NAME, DEFAULT_STATE_PLANNED));
            ctx.set("INDIVIDUAL::" + STATE_VALIDATED, properties.ontology().states().getOrDefault(PROPERTIES_VALIDATED_STATE_NAME, DEFAULT_STATE_VALIDATED));
            ctx.set("INDIVIDUAL::" + STATE_IN_PROGRESS, properties.ontology().states().getOrDefault(PROPERTIES_IN_PROGRESS_STATE_NAME, DEFAULT_STATE_IN_PROGRESS));
            ctx.set("INDIVIDUAL::" + STATE_SUCCEEDED, properties.ontology().states().getOrDefault(PROPERTIES_SUCCEEDED_STATE_NAME, DEFAULT_STATE_SUCCEEDED));
            ctx.set("INDIVIDUAL::" + STATE_FAILED, properties.ontology().states().getOrDefault(PROPERTIES_FAILED_STATE_NAME, DEFAULT_STATE_FAILED));
            ctx.set("INDIVIDUAL::" + STATE_COMPENSATING, properties.ontology().states().getOrDefault(PROPERTIES_COMPENSATING_STATE_NAME, DEFAULT_STATE_COMPENSATING));
        };
    }
}
