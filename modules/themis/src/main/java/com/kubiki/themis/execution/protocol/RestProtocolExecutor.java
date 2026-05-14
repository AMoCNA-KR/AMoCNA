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

/**
 * Generic REST Protocol Interpreter.
 * Executes hydrated REST requests.
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
    public ExecutionResult executeStateless(ActionMessage action) {
        return doExecute(action.instruction(), action.method(), action.payload(), action.actionId(), "stateless", action.expectedStatusCode());
    }

    private ExecutionResult doExecute(String url, HttpMethod method, String rawPayload, String actionId, String logContextId, int expectedStatusCode) {
        HttpMethod httpMethod = method != null ? method : HttpMethod.GET;
        String payload = rawPayload;

        log.info("Executing REST {} request to {} for {}", httpMethod, url, logContextId);

        try {
            RestClient.RequestBodySpec request = restClient.method(httpMethod).uri(url);

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
}
