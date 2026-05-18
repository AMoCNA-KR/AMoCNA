package com.kubiki.palamedes.templating.types;

import static com.kubiki.palamedes.templating.TemplatingConstants.TYPE_INDICATOR;

public record IntegerType(String key, Integer value) implements TemplatingType<Integer>  {
    @Override
    public String prefix() {
        return "INTEGER" + TYPE_INDICATOR;
    }

    @Override
    public String format() {
        return value.toString();
    }
}
