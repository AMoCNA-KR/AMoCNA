package com.kubiki.themis.execution.protocol;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.common.model.Protocol;
import com.kubiki.themis.model.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShellProtocolExecutor Tests")
class ShellProtocolExecutorTest {

    private ShellProtocolExecutor shellProtocolExecutor;

    @BeforeEach
    void setUp() {
        shellProtocolExecutor = new ShellProtocolExecutor();
    }

    @Test
    @DisplayName("Should return true only for SHELL protocol when checking support")
    void shouldSupportShellProtocol() {
        assertTrue(shellProtocolExecutor.supports(Protocol.SHELL));
        assertFalse(shellProtocolExecutor.supports(Protocol.REST));
    }

    @Test
    @DisplayName("Should execute command successfully when command is valid")
    void shouldExecuteEchoCommand() {
        // Given
        ActionMessage action = new ActionMessage(
                "action-shell-1",
                Protocol.SHELL,
                "echo \"Hello Themis\"",
                null,
                null,
                null,
                10,
                true,
                3,
                0
        );

        // When
        ExecutionResult result = shellProtocolExecutor.executeStateless(action);

        // Then
        assertTrue(result.success());
        assertEquals(0, result.observedStatusCode());
        assertEquals(ExecutionStatus.COMPLETED, result.status());
    }

    @Test
    @DisplayName("Should return failure when command fails")
    void shouldFailOnInvalidCommand() {
        // Given
        ActionMessage action = new ActionMessage(
                "action-shell-fail",
                Protocol.SHELL,
                "exit 1",
                null,
                null,
                null,
                10,
                true,
                3,
                0
        );

        // When
        ExecutionResult result = shellProtocolExecutor.executeStateless(action);

        // Then
        assertFalse(result.success());
        assertEquals(1, result.observedStatusCode());
        assertEquals(ExecutionStatus.FAILED_INTERNAL, result.status());
    }

    @Test
    @DisplayName("Should return failure when instruction is missing")
    void shouldReturnFalseWhenInstructionIsMissing() {
        // Given
        ActionMessage action = new ActionMessage(
                "action-shell-missing",
                Protocol.SHELL,
                null,
                null,
                null,
                null,
                10,
                true,
                3,
                0
        );

        // When
        ExecutionResult result = shellProtocolExecutor.executeStateless(action);

        // Then
        assertFalse(result.success());
        assertEquals(ExecutionStatus.FAILED_INTERNAL, result.status());
    }
}
