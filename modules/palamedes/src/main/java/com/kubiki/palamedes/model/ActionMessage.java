package com.kubiki.palamedes.model;

import org.springframework.http.HttpMethod;

public record ActionMessage(
    String actionId,
    Protocol protocol,
    String instruction,
    HttpMethod method,
    String payload,
    String authMechanism,
    int timeoutSeconds,
    boolean isIdempotent,
    int maxRetries,
    int expectedStatusCode
) {}
