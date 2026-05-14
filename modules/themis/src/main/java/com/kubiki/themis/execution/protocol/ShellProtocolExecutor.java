package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;

@Component
public class ShellProtocolExecutor implements ProtocolExecutor {
    private static final Logger log = LoggerFactory.getLogger(ShellProtocolExecutor.class);
    public static final String BIN_SH = "/bin/sh";

    @Override
    public boolean supports(Protocol protocol) {
        return Protocol.SHELL.equals(protocol);
    }

    @Override
    public ExecutionResult executeStateless(ActionMessage action) {
        return executeCommand(action.instruction(), action.actionId(), "stateless", action.expectedStatusCode());
    }

    private ExecutionResult executeCommand(String command, String actionId, String logContextId, int expectedStatusCode) {
        if (command == null || command.isBlank()) {
            log.error("Instruction is null or blank for action {} in context {}", actionId, logContextId);
            return ExecutionResult.failure(1, "Blank instruction");
        }

        log.info("Executing shell command for {}: {}", logContextId, command);

        try {
            ProcessBuilder pb = new ProcessBuilder();
            Process process = pb.command(BIN_SH, "-c", command).start();
            // Capture output in separate threads to avoid hanging
            Thread.ofVirtual().start(() -> readStream(process.inputReader(), "STDOUT", actionId));
            Thread.ofVirtual().start(() -> readStream(process.errorReader(), "STDERR", actionId));

            int exitCode = process.waitFor();
            log.info("Shell command {} exited with code {}", actionId, exitCode);

            return new ExecutionResult(exitCode, exitCode == expectedStatusCode, exitCode == expectedStatusCode ? null : "Command failed with exit code " + exitCode);
        } catch (Exception e) {
            log.error("Failed to execute shell action {}: {}", actionId, e.getMessage());
            return ExecutionResult.failure(1, e.getMessage());
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
