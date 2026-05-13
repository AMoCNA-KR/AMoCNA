package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.ActionMessage;
import com.kubiki.themis.model.Protocol;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.util.Map;
import java.util.UUID;

@Component
public class ShellProtocolExecutor implements ProtocolExecutor {
    private static final Logger log = LoggerFactory.getLogger(ShellProtocolExecutor.class);
    private static final int SUCCESS_EXIT_CODE = 0;
    public static final String ENV_THEMIS_PREFIX = "THEMIS_";
    public static final String COMMAND_SANITIZE_REGEX = "[^A-Z0-9_]";
    public static final String ENV_PREFIX = "$";
    public static final String BIN_SH = "/bin/sh";

    @Override
    public boolean supports(Protocol protocol) {
        return Protocol.SHELL.equals(protocol);
    }

    @Override
    public boolean execute(ActionData action, UUID executionId) {
        if (!(action instanceof ActionData.SimpleAction simpleAction)) {
            log.error("Action {} is not a SimpleAction", action.id());
            return false;
        }
        return executeCommand(simpleAction.instruction(), simpleAction.data(), action.id().toString(), executionId.toString());
    }

    @Override
    public boolean executeStateless(ActionMessage action) {
        return executeCommand(action.instruction(), action.data(), action.actionId(), "stateless");
    }

    private boolean executeCommand(String template, Map<String, String> data, String actionId, String logContextId) {
        if (template == null || template.isBlank()) {
            log.error("Instruction is null or blank for action {} in context {}", actionId, logContextId);
            return false;
        }

        ProcessBuilder pb = new ProcessBuilder();
        Map<String, String> env = pb.environment();

        String command = template;
        if (data != null) {
            for (var entry : data.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                // Map placeholder {key} to environment variable $THEMIS_KEY
                String envVarName = ENV_THEMIS_PREFIX + key.toUpperCase().replaceAll(COMMAND_SANITIZE_REGEX, "_");
                command = command.replace("{" + key + "}", ENV_PREFIX + envVarName);
                env.put(envVarName, value);
            }
        }

        log.info("Executing shell command for {}: {}", logContextId, command);

        try {
            Process process = pb.command(BIN_SH, "-c", command).start();
            // Capture output in separate threads to avoid hanging
            Thread.ofVirtual().start(() -> readStream(process.inputReader(), "STDOUT", actionId));
            Thread.ofVirtual().start(() -> readStream(process.errorReader(), "STDERR", actionId));

            int exitCode = process.waitFor();
            log.info("Shell command {} exited with code {}", actionId, exitCode);

            return exitCode == SUCCESS_EXIT_CODE;
        } catch (Exception e) {
            log.error("Failed to execute shell action {}: {}", actionId, e.getMessage());
            return false;
        }
    }

    private void readStream(BufferedReader br, String type, String actionId) {
        try {
            String line;
            while ((line = br.readLine()) != null) {
                log.info("[{}] {}: {}", actionId, type, line);
            }
        } catch (Exception e) {
            log.error("Error reading {} for action {}: {}", type, actionId, e.getMessage());
        }
    }
}
