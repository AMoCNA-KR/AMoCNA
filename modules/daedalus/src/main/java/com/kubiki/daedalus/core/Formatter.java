package com.kubiki.daedalus.core;

import com.kubiki.daedalus.annotation.TemplateType;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.kubiki.daedalus.core.DaedalusConstants.*;

public class Formatter {
    public String format(Object value, TemplateType type) {
        if (value == null) return EMPTY_STRING;
        return switch (type) {
            case IRI -> IRI_BEGIN + value.toString() + IRI_END;
            case COLLECTION -> formatCollection((Collection<?>) value);
            case LITERAL -> formatLiteral(value);
            case PLAIN -> value.toString();
        };
    }

    private String formatLiteral(Object value) {
        if (value instanceof String) return QUOTE + value + QUOTE;
        return value.toString();
    }

    private String formatCollection(Collection<?> col) {
        return col.stream()
                .map(this::formatLiteral)
                .collect(Collectors.joining(COLLECTION_SEPARATOR));
    }
}

