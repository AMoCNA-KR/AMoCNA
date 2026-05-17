package com.kubiki.daedalus.core.format;

import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.core.ValueFormatter;
import org.springframework.stereotype.Component;
import static com.kubiki.daedalus.core.DaedalusConstants.QUOTE;

@Component
public class LiteralFormatter implements ValueFormatter<Object> {
    @Override
    public String format(Object value) {
        if (value instanceof String) {
            return QUOTE + value + QUOTE;
        }
        return value != null ? value.toString() : "";
    }
    @Override
    public Class<Object> getSupportedType() { return null; }
    @Override
    public TemplateType getAnnotationType() { return TemplateType.LITERAL; }
}
