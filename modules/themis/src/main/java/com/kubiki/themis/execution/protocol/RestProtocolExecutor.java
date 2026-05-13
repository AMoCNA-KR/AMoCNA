package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Generic REST Protocol Interpreter.
 * Ingests URL templates from GraphDB and executes them.
 */
@Component
public class RestProtocolExecutor implements ProtocolExecutor {
    private static final Logger log = LoggerFactory.getLogger(RestProtocolExecutor.class);
    private final RestClient restClient;

    public RestProtocolExecutor(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean supports(Protocol protocol) {
        return Protocol.REST.equals(protocol);
    }

    @Override
    public ExecutionResult execute(ActionData action, UUID executionId) {
        if (!(action instanceof ActionData.SimpleAction simpleAction)) {
            log.error("Action {} is not a SimpleAction", action.id());
            return ExecutionResult.failure(500, "Not a SimpleAction");
        }
        return doExecute(simpleAction.instruction(), simpleAction.data(), simpleAction.method(), simpleAction.payload(), action.id().toString(), executionId.toString(), simpleAction.expectedStatusCode());
    }

    @Override
    public ExecutionResult executeStateless(ActionMessage action) {
        return doExecute(action.instruction(), action.data(), action.method(), action.payload(), action.actionId(), "stateless", action.expectedStatusCode());
    }

    private ExecutionResult doExecute(String urlTemplate, Map<String, String> variables, HttpMethod method, String rawPayload, String actionId, String logContextId, int expectedStatusCode) {
        variables = variables != null ? variables : Map.of();
        HttpMethod httpMethod = method != null ? method : HttpMethod.GET;
        String payload = rawPayload != null ? hydrate(rawPayload, variables) : null;

        log.info("Executing REST {} request to {} for {}", httpMethod, urlTemplate, logContextId);

        try {
            RestClient.RequestBodySpec request = restClient.method(httpMethod).uri(urlTemplate, variables);

            if (payload != null && !payload.isEmpty()) {
                request.body(payload);
            }

            ResponseEntity<Void> response = request.retrieve().toBodilessEntity();
            int observed = response.getStatusCode().value();
            return new ExecutionResult(observed, observed == expectedStatusCode, null);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            int observed = e.getStatusCode().value();
            log.error("REST action {} failed with status {}: {}", actionId, observed, e.getMessage());
            return new ExecutionResult(observed, observed == expectedStatusCode, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to execute REST action {} (context {}): {}", actionId, logContextId, e.getMessage());
            return ExecutionResult.failure(500, e.getMessage());
        }
    }

    private String hydrate(String template, Map<String, String> data) {
        if (template == null) return null;
        String result = template;
        for (var entry : data.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}

