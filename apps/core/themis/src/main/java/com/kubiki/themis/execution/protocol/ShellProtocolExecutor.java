package com.kubiki.themis.execution.protocol;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.common.model.Protocol;
import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;

/**
 * Generic Shell Protocol Interpreter.
 * Executes hydrated Shell commands.
 */
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
        return executeCommand(action);
    }

    private ExecutionResult executeCommand(ActionMessage action) {
        String command = action.instruction();
        String actionId = action.actionId();
        int expectedStatusCode = action.expectedStatusCode();

        if (command == null || command.isBlank()) {
            log.error("Instruction is null or blank for action {}", actionId);
            return ExecutionResult.failure(1, "Blank instruction", ExecutionStatus.FAILED_INTERNAL);
        }

        log.info("Executing shell command for action {}: {}", actionId, command);

        try {
            ProcessBuilder pb = new ProcessBuilder();
            Process process = pb.command(BIN_SH, "-c", command).start();
            
            Thread.ofVirtual().start(() -> readStream(process.inputReader(), "STDOUT", actionId));
            Thread.ofVirtual().start(() -> readStream(process.errorReader(), "STDERR", actionId));

            int exitCode = process.waitFor();
            log.info("Shell command {} exited with code {}", actionId, exitCode);

            if (exitCode == expectedStatusCode) {
                return ExecutionResult.success(exitCode);
            } else {
                return ExecutionResult.failure(exitCode, "Command failed with exit code " + exitCode, ExecutionStatus.FAILED_INTERNAL);
            }
        } catch (InterruptedException e) {
            log.error("Shell action {} was interrupted: {}", actionId, e.getMessage());
            Thread.currentThread().interrupt();
            return ExecutionResult.failure(1, e.getMessage(), ExecutionStatus.FAILED_TIMEOUT);
        } catch (Exception e) {
            log.error("Failed to execute shell action {}: {}", actionId, e.getMessage());
            return ExecutionResult.failure(1, e.getMessage(), ExecutionStatus.FAILED_INTERNAL);
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
