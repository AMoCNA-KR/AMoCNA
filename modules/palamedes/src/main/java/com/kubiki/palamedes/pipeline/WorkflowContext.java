package com.kubiki.palamedes.pipeline;

import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public record WorkflowContext(
    IRI actionId,
    ActionData actionData,
    Map<String, Object> metadata
) {
    public WorkflowContext(IRI actionId, ActionData actionData) {
        this(actionId, actionData, new ConcurrentHashMap<>());
    }
}
