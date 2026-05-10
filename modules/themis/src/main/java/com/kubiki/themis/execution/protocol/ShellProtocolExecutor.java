package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.util.UUID;

@Component
public class ShellProtocolExecutor implements ProtocolExecutor {
    private static final Logger log = LoggerFactory.getLogger(ShellProtocolExecutor.class);
    private static final int SUCCESS_EXIT_CODE = 0;

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

        String template = simpleAction.instruction();
        if (template == null || template.isBlank()) {
            log.error("Instruction is null or blank for action {} in execution {}", action.id(), executionId);
            return false;
        }

        ProcessBuilder pb = new ProcessBuilder();
        java.util.Map<String, String> env = pb.environment();

        String command = template;
        for (var entry : simpleAction.data().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // Map placeholder {key} to environment variable $THEMIS_KEY
            String envVarName = "THEMIS_" + key.toUpperCase().replaceAll("[^A-Z0-9_]", "_");
            command = command.replace("{" + key + "}", "$" + envVarName);
            env.put(envVarName, value);
        }

        log.info("Executing shell command for execution {}: {}", executionId, command);

        try {
            Process process = pb.command("/bin/sh", "-c", command).start();
            // Capture output in separate threads to avoid hanging
            Thread.ofVirtual().start(() -> readStream(process.inputReader(), "STDOUT", action.id()));
            Thread.ofVirtual().start(() -> readStream(process.errorReader(), "STDERR", action.id()));

            int exitCode = process.waitFor();
            log.info("Shell command {} exited with code {}", action.id(), exitCode);

            return exitCode == SUCCESS_EXIT_CODE;
        } catch (Exception e) {
            log.error("Failed to execute shell action {}: {}", action.id(), e.getMessage());
            return false;
        }
    }

    private void readStream(BufferedReader br, String type, IRI actionId) {
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
