package com.kubiki.palamedes.templating.types;

import static com.kubiki.palamedes.templating.TemplatingConstants.TYPE_INDICATOR;

public record PrefixType(String key, String value) implements TemplatingType<String> {
    @Override
    public String prefix() {
        return "PREFIX" + TYPE_INDICATOR;
    }

    @Override
    public String format() {
        return value;
    }
}
