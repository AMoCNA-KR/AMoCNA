package com.kubiki.themis.model;

public record ActionStatusUpdate(
    String actionId,
    ExecutionStatus status,
    String errorMessage,
    int observedStatusCode
) {}
