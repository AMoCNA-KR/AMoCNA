package com.kubiki.palamedes.templating.types;

import static com.kubiki.palamedes.templating.TemplatingConstants.TYPE_INDICATOR;

public record IndividualType(String key, String value) implements TemplatingType<String> {
    @Override
    public String prefix() {
        return "INDIVIDUAL" + TYPE_INDICATOR;
    }

    @Override
    public String format() {
        return value;
    }
}
