package com.kubiki.palamedes.templating.types;

import static com.kubiki.palamedes.templating.TemplatingConstants.TYPE_INDICATOR;

public record BooleanType(String key, Boolean value) implements TemplatingType<Boolean> {

    @Override
    public String prefix() {
        return "BOOLEAN" + TYPE_INDICATOR;
    }

    @Override
    public String format() {
        return value.toString();
    }
}
