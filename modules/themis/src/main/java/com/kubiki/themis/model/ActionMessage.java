package com.kubiki.themis.model;

import org.springframework.http.HttpMethod;
import java.util.Map;

public record ActionMessage(
    String actionId,
    Protocol protocol,
    String instruction,
    HttpMethod method,
    String payload,
    Map<String, String> data, // For hydration variables
    String authMechanism,
    int timeoutSeconds,
    boolean isIdempotent,
    int maxRetries,
    int expectedStatusCode
) {}
