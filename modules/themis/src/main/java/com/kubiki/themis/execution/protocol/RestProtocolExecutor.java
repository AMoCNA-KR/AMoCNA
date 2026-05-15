package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Generic REST Protocol Interpreter.
 * Executes hydrated REST requests with granular feedback.
 */
@Component
public class RestProtocolExecutor implements ProtocolExecutor {
    private static final Logger log = LoggerFactory.getLogger(RestProtocolExecutor.class);
    private final RestClient restClient;
    private final ThemisProperties themisProperties;

    public RestProtocolExecutor(RestClient.Builder restClientBuilder, ThemisProperties themisProperties) {
        this.restClient = restClientBuilder.build();
        this.themisProperties = themisProperties;
    }

    @Override
    public boolean supports(Protocol protocol) {
        return Protocol.REST.equals(protocol);
    }

    @Override
    public ExecutionResult executeStateless(ActionMessage action) {
        return doExecute(action);
    }

    private ExecutionResult doExecute(ActionMessage action) {
        HttpMethod httpMethod = action.method() != null ? action.method() : HttpMethod.GET;
        String url = action.instruction();
        String payload = action.payload();
        int expectedStatusCode = action.expectedStatusCode();

        log.info("Executing REST {} request to {} for action {}", httpMethod, url, action.actionId());

        try {
            RestClient.RequestBodySpec request = restClient.method(httpMethod).uri(url);

            // Auth Injection
            if ("BearerToken".equalsIgnoreCase(action.authMechanism())) {
                String token = themisProperties.secret().bearerToken();
                if (token != null && !token.isEmpty()) {
                    request.header("Authorization", "Bearer " + token);
                } else {
                    log.warn("BearerToken requested but themis.secret.bearer-token is missing");
                }
            }

            if (payload != null && !payload.isEmpty()) {
                request.body(payload);
            }

            ResponseEntity<Void> response = request.retrieve().toBodilessEntity();
            int observed = response.getStatusCode().value();
            
            if (observed == expectedStatusCode) {
                return ExecutionResult.success(observed);
            } else {
                return ExecutionResult.failure(observed, "Unexpected status code: " + observed, ExecutionStatus.FAILED_HTTP);
            }

        } catch (HttpClientErrorException e) {
            int observed = e.getStatusCode().value();
            log.error("REST action {} failed with client error {}: {}", action.actionId(), observed, e.getMessage());
            ExecutionStatus status = (observed == 401 || observed == 403) ? ExecutionStatus.FAILED_AUTH : ExecutionStatus.FAILED_HTTP;
            return ExecutionResult.failure(observed, e.getMessage(), status);
        } catch (HttpServerErrorException e) {
            int observed = e.getStatusCode().value();
            log.error("REST action {} failed with server error {}: {}", action.actionId(), observed, e.getMessage());
            return ExecutionResult.failure(observed, e.getMessage(), ExecutionStatus.FAILED_HTTP);
        } catch (ResourceAccessException e) {
            log.error("REST action {} failed with timeout or connection error: {}", action.actionId(), e.getMessage());
            return ExecutionResult.failure(504, e.getMessage(), ExecutionStatus.FAILED_TIMEOUT);
        } catch (Exception e) {
            log.error("Failed to execute REST action {}: {}", action.actionId(), e.getMessage());
            return ExecutionResult.failure(500, e.getMessage(), ExecutionStatus.FAILED_INTERNAL);
        }
    }
}
