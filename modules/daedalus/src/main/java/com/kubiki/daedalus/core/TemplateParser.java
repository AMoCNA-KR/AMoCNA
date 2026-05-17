package com.kubiki.daedalus.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateParser {
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    public List<TemplateToken> parse(String template) {
        List<TemplateToken> tokens = new ArrayList<>();
        Matcher matcher = VAR_PATTERN.matcher(template);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                tokens.add(new TemplateToken.StaticToken(template.substring(lastEnd, matcher.start())));
            }
            tokens.add(new TemplateToken.VariableToken(matcher.group(1)));
            lastEnd = matcher.end();
        }
        if (lastEnd < template.length()) {
            tokens.add(new TemplateToken.StaticToken(template.substring(lastEnd)));
        }
        return tokens;
    }
}
