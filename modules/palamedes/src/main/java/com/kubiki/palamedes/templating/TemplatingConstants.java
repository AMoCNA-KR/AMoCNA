package com.kubiki.palamedes.templating;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TemplatingConstants {
    public static final String BEGIN_OF_VARIABLE = "${";
    public static final String END_OF_VARIABLE = "}";
    public static final String ESCAPED_APOSTROPHE = "\"";
    public static final String BEGIN_OF_IRI_VARIABLE = "<";
    public static final String END_OF_IRI_VARIABLE = ">";
    public static final String SPARQL_PREFIX = "PREFIX ";
    public static final String TYPE_INDICATOR = "::";

    public static final String ACTIONS_PREFIX_VARIABLE = "ACTIONS_PREFIX";
    public static final String RESOURCES_PREFIX_VARIABLE = "RESOURCES_PREFIX";
    public static final String BRIDGE_PREFIX_VARIABLE = "BRIDGE_PREFIX";

}
