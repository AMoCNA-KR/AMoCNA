package com.kubiki.themis.execution;

import com.kubiki.themis.model.ActionMessage;
import com.kubiki.themis.model.ExecutionResult;
import com.kubiki.themis.model.Protocol;

/**
 * Generic interface for protocol-specific execution.
 */
public interface ProtocolExecutor {
    boolean supports(Protocol protocol);

    ExecutionResult executeStateless(ActionMessage action);
}
