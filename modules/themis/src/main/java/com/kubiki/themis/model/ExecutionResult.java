package com.kubiki.themis.model;

/**
 * Result of a protocol-specific execution.
 * 
 * @param observedStatusCode The actual status code (HTTP) or exit code (Shell) received.
 * @param success Whether the execution is considered successful based on expected criteria.
 * @param errorMessage Optional error message if execution failed.
 */
public record ExecutionResult(
    int observedStatusCode,
    boolean success,
    String errorMessage
) {
    public static ExecutionResult success(int statusCode) {
        return new ExecutionResult(statusCode, true, null);
    }
    
    public static ExecutionResult failure(int statusCode, String errorMessage) {
        return new ExecutionResult(statusCode, false, errorMessage);
    }
}
