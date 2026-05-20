package com.kubiki.themis.execution;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.themis.model.ExecutionResult;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.common.model.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ActionQueueListenerTest {

    private ProtocolExecutor executor;
    private StatusProducer statusProducer;
    private ActionQueueListener listener;

    @BeforeEach
    void setUp() {
        executor = mock(ProtocolExecutor.class);
        statusProducer = mock(StatusProducer.class);
        listener = new ActionQueueListener(List.of(executor), statusProducer);
    }

    @Test
    void shouldExecuteActionSuccessfully() {
        ActionMessage message = new ActionMessage(
            "action1", Protocol.REST, "instruction", "GET", null,
            null, 30, true, 3, 200
        );
        when(executor.supports(Protocol.REST)).thenReturn(true);
        when(executor.executeStateless(message)).thenReturn(ExecutionResult.success(200));

        listener.receiveAction(message);

        ArgumentCaptor<ActionStatusUpdate> captor = ArgumentCaptor.forClass(ActionStatusUpdate.class);
        verify(statusProducer).sendUpdate(captor.capture());
        ActionStatusUpdate update = captor.getValue();

        assertEquals("action1", update.actionId());
        assertEquals(ExecutionStatus.COMPLETED, update.status());
        assertNull(update.errorMessage());
        assertEquals(200, update.observedStatusCode());
    }

    @Test
    void shouldHandleExecutorNotFound() {
        // Updated Protocol enum might not have GRPC anymore if it was removed, 
        // but let's assume Protocol.REST is there and we don't have an executor for it
        ActionMessage message = new ActionMessage(
            "action1", Protocol.REST, "instruction", null, null,
            null, 30, true, 3, 200
        );
        // Ensure no executor supports this protocol
        ProtocolExecutor otherExecutor = mock(ProtocolExecutor.class);
        when(otherExecutor.supports(any())).thenReturn(false);
        ActionQueueListener singleListener = new ActionQueueListener(List.of(otherExecutor), statusProducer);

        singleListener.receiveAction(message);

        ArgumentCaptor<ActionStatusUpdate> captor = ArgumentCaptor.forClass(ActionStatusUpdate.class);
        verify(statusProducer).sendUpdate(captor.capture());
        ActionStatusUpdate update = captor.getValue();

        assertEquals(ExecutionStatus.FAILED_INTERNAL, update.status());
        assertEquals("No executor for protocol", update.errorMessage());
    }

    @Test
    void shouldHandleExecutionFailure() {
        ActionMessage message = new ActionMessage(
            "action1", Protocol.REST, "instruction", "POST", null,
            null, 30, true, 3, 201
        );
        when(executor.supports(Protocol.REST)).thenReturn(true);
        when(executor.executeStateless(message)).thenReturn(ExecutionResult.failure(500, "Execution failed", ExecutionStatus.FAILED_HTTP));

        listener.receiveAction(message);

        ArgumentCaptor<ActionStatusUpdate> captor = ArgumentCaptor.forClass(ActionStatusUpdate.class);
        verify(statusProducer).sendUpdate(captor.capture());
        ActionStatusUpdate update = captor.getValue();

        assertEquals(ExecutionStatus.FAILED_HTTP, update.status());
        assertEquals("Execution failed", update.errorMessage());
        assertEquals(500, update.observedStatusCode());
    }

    @Test
    void shouldRetryOnRetryableFailure() {
        ActionMessage message = new ActionMessage(
            "action-retry", Protocol.REST, "instruction", "GET", null,
            null, 10, true, 1, 200 // 1 retry
        );
        when(executor.supports(Protocol.REST)).thenReturn(true);
        
        // Fail first time with timeout, succeed second time
        when(executor.executeStateless(message))
            .thenReturn(ExecutionResult.failure(504, "Timeout", ExecutionStatus.FAILED_TIMEOUT))
            .thenReturn(ExecutionResult.success(200));

        listener.receiveAction(message);

        verify(executor, times(2)).executeStateless(message);
        verify(statusProducer).sendUpdate(argThat(u -> u.status() == ExecutionStatus.COMPLETED));
    }
}
