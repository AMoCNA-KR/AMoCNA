package com.kubiki.palamedes.templating.types;

public sealed interface TemplatingType<T> permits IriType, BooleanType, IntegerType, FloatType, StringType, IndividualType, PrefixType {
    String key();
    String prefix();
    T value();
    String format();
}
