package com.kubiki.palamedes.templating.types;


import static com.kubiki.palamedes.templating.TemplatingConstants.ESCAPED_APOSTROPHE;
import static com.kubiki.palamedes.templating.TemplatingConstants.TYPE_INDICATOR;

public record StringType(String key, String value) implements TemplatingType<String> {
    @Override
    public String prefix() {
        return "STRING" + TYPE_INDICATOR;
    }

    @Override
    public String format() {
        return ESCAPED_APOSTROPHE + escapeSparqlString(value) + ESCAPED_APOSTROPHE;
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
