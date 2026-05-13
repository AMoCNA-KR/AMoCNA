package com.kubiki.themis.execution;

import com.kubiki.themis.model.ActionMessage;
import com.kubiki.themis.model.ActionStatusUpdate;
import com.kubiki.themis.model.ExecutionStatus;
import com.kubiki.themis.model.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.util.Collections;
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
            "action1", Protocol.REST, "instruction", HttpMethod.GET, null,
            Collections.emptyMap(), null, 30, true, 3, 200
        );
        when(executor.supports(Protocol.REST)).thenReturn(true);
        when(executor.executeStateless(message)).thenReturn(true);

        listener.receiveAction(message);

        ArgumentCaptor<ActionStatusUpdate> captor = ArgumentCaptor.forClass(ActionStatusUpdate.class);
        verify(statusProducer).sendUpdate(captor.capture());
        ActionStatusUpdate update = captor.getValue();

        assertEquals("action1", update.actionId());
        assertEquals(ExecutionStatus.SUCCESS, update.status());
        assertNull(update.errorMessage());
        assertEquals(200, update.observedStatusCode());
    }

    @Test
    void shouldHandleExecutorNotFound() {
        ActionMessage message = new ActionMessage(
            "action1", Protocol.GRPC, "instruction", null, null,
            Collections.emptyMap(), null, 30, true, 3, 200
        );
        when(executor.supports(any())).thenReturn(false);

        listener.receiveAction(message);

        ArgumentCaptor<ActionStatusUpdate> captor = ArgumentCaptor.forClass(ActionStatusUpdate.class);
        verify(statusProducer).sendUpdate(captor.capture());
        ActionStatusUpdate update = captor.getValue();

        assertEquals(ExecutionStatus.FAILED, update.status());
        assertEquals("No executor for protocol", update.errorMessage());
    }

    @Test
    void shouldHandleExecutionFailure() {
        ActionMessage message = new ActionMessage(
            "action1", Protocol.REST, "instruction", HttpMethod.POST, null,
            Collections.emptyMap(), null, 30, true, 3, 201
        );
        when(executor.supports(Protocol.REST)).thenReturn(true);
        when(executor.executeStateless(message)).thenReturn(false);

        listener.receiveAction(message);

        ArgumentCaptor<ActionStatusUpdate> captor = ArgumentCaptor.forClass(ActionStatusUpdate.class);
        verify(statusProducer).sendUpdate(captor.capture());
        ActionStatusUpdate update = captor.getValue();

        assertEquals(ExecutionStatus.FAILED, update.status());
        assertEquals("Execution failed", update.errorMessage());
        assertEquals(500, update.observedStatusCode());
    }
}
