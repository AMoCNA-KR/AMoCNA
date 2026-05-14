package com.kubiki.palamedes.planner;

import com.kubiki.palamedes.model.ActionMessage;
import com.kubiki.palamedes.model.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class PlannerService {
    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    public String hydrate(String template, Map<String, String> data) {
        if (template == null) return null;
        if (data == null) return template;
        String result = template;
        for (var entry : data.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    // Placeholder for blueprint loading logic
    public ActionMessage buildActionMessage(String resourceName, String intentIri) {
        log.info("Planning action for resource {} with intent {}", resourceName, intentIri);
        
        // In a real implementation, this would load MoaMont blueprint from GraphDB
        // and hydrate it. For now, we return a mock message based on the blueprint requirements.
        
        String actionId = UUID.randomUUID().toString();
        
        return new ActionMessage(
            actionId,
            Protocol.REST,
            hydrate("https://kubernetes.default.svc/api/v1/namespaces/default/pods/{resourceName}/restart", Map.of("resourceName", resourceName)),
            HttpMethod.POST,
            null,
            "BearerToken",
            30,
            true,
            3,
            200
        );
    }
}
