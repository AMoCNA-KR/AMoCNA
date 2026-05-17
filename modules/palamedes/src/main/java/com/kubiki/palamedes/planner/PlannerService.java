package com.kubiki.palamedes.planner;

import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.kubiki.palamedes.knowledge.KnowledgeConstants.BEGIN_OF_VARIABLE;
import static com.kubiki.palamedes.knowledge.KnowledgeConstants.END_OF_VARIABLE;

@Service
public class PlannerService {
    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    public String hydrate(String template, Map<String, String> data) {
        if (template == null) return null;
        if (data == null) return template;
        String result = template;
        for (var entry : data.entrySet()) {
            result = result.replace(BEGIN_OF_VARIABLE + entry.getKey() + END_OF_VARIABLE, entry.getValue());
        }
        return result;
    }

    public ActionMessage buildActionMessage(ActionData.SimpleAction action, Map<String, String> contextData) {
        log.info("Planning message for action {} (protocol: {})", action.id(), action.protocol());
        
        String instruction = hydrate(action.instruction(), contextData);
        String payload = hydrate(action.payload(), contextData);
        
        return new ActionMessage(
            action.id().toString(),
            action.protocol(),
            instruction,
            action.method() != null ? action.method().name() : null,
            payload,
            action.authMechanism(),
            action.timeoutSeconds(),
            action.isIdempotent(),
            action.maxRetries(),
            action.expectedStatusCode()
        );
    }
}
