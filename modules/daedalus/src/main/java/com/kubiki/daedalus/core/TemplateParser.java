package com.kubiki.daedalus.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kubiki.daedalus.core.DaedalusConstants.*;

public class TemplateParser {

    public List<TemplateToken> parse(String template) {
        if (template == null) return List.of();
        List<TemplateToken> tokens = new ArrayList<>();
        Matcher matcher = VAR_PATTERN.matcher(template);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                tokens.add(new TemplateToken.StaticToken(template.substring(lastEnd, matcher.start())));
            }
            String content = matcher.group(1);
            String name = content;
            String defaultValue = null;
            
            if (content.contains(DEFAULT_VALUE_SEPARATOR)) {
                int idx = content.indexOf(DEFAULT_VALUE_SEPARATOR);
                name = content.substring(0, idx);
                defaultValue = content.substring(idx + DEFAULT_VALUE_SEPARATOR.length());
            }
            
            tokens.add(new TemplateToken.VariableToken(name, defaultValue));
            lastEnd = matcher.end();
        }
        if (lastEnd < template.length()) {
            tokens.add(new TemplateToken.StaticToken(template.substring(lastEnd)));
        }
        return tokens;
    }
}
