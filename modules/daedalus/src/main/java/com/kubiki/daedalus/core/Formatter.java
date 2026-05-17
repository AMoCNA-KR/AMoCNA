package com.kubiki.daedalus.core;

import com.kubiki.daedalus.annotation.TemplateType;
import java.util.Collection;
import java.util.stream.Collectors;

public class Formatter {
    public String format(Object value, TemplateType type) {
        if (value == null) return "";
        return switch (type) {
            case IRI -> "<" + value + ">";
            case COLLECTION -> formatCollection((Collection<?>) value);
            case LITERAL -> formatLiteral(value);
            case PLAIN -> value.toString();
        };
    }

    private String formatLiteral(Object value) {
        if (value instanceof String) return "\"" + value + "\"";
        return value.toString();
    }

    private String formatCollection(Collection<?> col) {
        return col.stream()
                .map(this::formatLiteral)
                .collect(Collectors.joining(", "));
    }
}
