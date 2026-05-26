package com.kubiki.daedalus.core.format;

import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.core.ValueFormatter;
import org.springframework.stereotype.Component;

import static com.kubiki.daedalus.core.DaedalusConstants.QUOTE;

public class LiteralFormatter implements ValueFormatter<Object> {
    @Override
    public String format(Object value) {
        if (value != null) {
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            return QUOTE + value.toString() + QUOTE;
        }
        return "";
    }

    @Override
    public Class<Object> getSupportedType() {
        return null;
    }

    @Override
    public TemplateType getAnnotationType() {
        return TemplateType.LITERAL;
    }
}
