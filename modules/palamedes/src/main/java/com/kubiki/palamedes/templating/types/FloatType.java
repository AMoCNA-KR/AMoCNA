package com.kubiki.palamedes.templating.types;

import static com.kubiki.palamedes.templating.TemplatingConstants.TYPE_INDICATOR;

public record FloatType(String key, Float value) implements TemplatingType<Float> {
    @Override
    public String prefix() {
        return "FLOAT" + TYPE_INDICATOR;
    }

    @Override
    public String format() {
        return value.toString();
    }
}
