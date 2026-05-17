package com.kubiki.daedalus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.exception.HydrationException;
import com.kubiki.daedalus.exception.TemplateMappingException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultDaedalusHydrator implements DaedalusHydrator {
    private final TemplateParser parser;
    private final Formatter formatter;
    private final GlobalTemplateContext globalContext;
    private final ObjectMapper objectMapper;

    public DefaultDaedalusHydrator(TemplateParser parser, Formatter formatter, GlobalTemplateContext globalContext, ObjectMapper objectMapper) {
        this.parser = parser;
        this.formatter = formatter;
        this.globalContext = globalContext;
        this.objectMapper = objectMapper;
    }

    @Override
    public String hydrate(String template, Map<String, Object> data) {
        List<TemplateToken> tokens = parser.parse(template);
        Map<String, String> values = new HashMap<>(globalContext.getAll());
        
        if (data != null) {
            data.forEach((k, v) -> {
                values.put(k, formatter.format(v, null));
            });
        }

        StringBuilder sb = new StringBuilder();
        for (TemplateToken token : tokens) {
            if (token instanceof TemplateToken.StaticToken s) {
                sb.append(s.text());
            } else if (token instanceof TemplateToken.VariableToken v) {
                String val = values.get(v.name());
                if (val == null) {
                    val = v.defaultValue();
                }
                if (val == null) {
                    throw new HydrationException("Missing variable: " + v.name());
                }
                sb.append(val);
            }
        }
        return sb.toString();
    }

    @Override
    public <T> T hydrateAndMap(String template, Map<String, Object> data, Class<T> targetClass) {
        String hydrated = hydrate(template, data);
        if (targetClass.equals(String.class)) {
            return (T) hydrated;
        }
        try {
            return objectMapper.readValue(hydrated, targetClass);
        } catch (Exception e) {
            throw new TemplateMappingException("Failed to map hydrated template to " + targetClass.getSimpleName(), e);
        }
    }
}
