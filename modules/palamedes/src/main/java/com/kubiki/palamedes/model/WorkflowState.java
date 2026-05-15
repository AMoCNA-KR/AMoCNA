package com.kubiki.palamedes.model;

public enum WorkflowState {
    INITIAL("State_Initial"),
    PLANNED("State_Planned"),
    VALIDATED("State_Validated"),
    IN_PROGRESS("State_InProgress"),
    SUCCEEDED("State_Succeeded"),
    FAILED("State_Failed"),
    COMPENSATING("State_Compensating");

    private final String fragment;
    WorkflowState(String fragment) { this.fragment = fragment; }
    public String getFragment() { return fragment; }
    
    public static WorkflowState fromFragment(String fragment) {
        for (WorkflowState state : values()) {
            if (state.fragment.equalsIgnoreCase(fragment)) return state;
        }
        throw new IllegalArgumentException("Unknown state fragment: " + fragment);
    }
}
