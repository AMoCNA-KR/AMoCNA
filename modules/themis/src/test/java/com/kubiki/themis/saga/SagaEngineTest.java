package com.kubiki.themis.saga;

import com.kubiki.themis.execution.ActionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SagaEngineTest {

    private SagaEngine sagaEngine;
    private ActionExecutor executor1;
    private ActionExecutor executor2;

    @BeforeEach
    void setUp() {
        sagaEngine = new SagaEngine();
        executor1 = mock(ActionExecutor.class);
        executor2 = mock(ActionExecutor.class);
    }

    @Test
    void shouldExecuteAllStepsSuccessfully() {
        when(executor1.execute(anyString())).thenReturn(true);
        when(executor2.execute(anyString())).thenReturn(true);

        sagaEngine.addStep(new SagaEngine.Step("step1", executor1, "target1"));
        sagaEngine.addStep(new SagaEngine.Step("step2", executor2, "target2"));

        boolean result = sagaEngine.run();

        assertTrue(result);
        verify(executor1, times(1)).execute("target1");
        verify(executor2, times(1)).execute("target2");
        verify(executor1, never()).compensate(anyString());
        verify(executor2, never()).compensate(anyString());
    }

    @Test
    void shouldCompensateWhenStepFails() {
        when(executor1.execute(anyString())).thenReturn(true);
        when(executor2.execute(anyString())).thenReturn(false);

        sagaEngine.addStep(new SagaEngine.Step("step1", executor1, "target1"));
        sagaEngine.addStep(new SagaEngine.Step("step2", executor2, "target2"));

        boolean result = sagaEngine.run();

        assertFalse(result);
        verify(executor1, times(1)).execute("target1");
        verify(executor2, times(1)).execute("target2");
        verify(executor1, times(1)).compensate("target1");
        verify(executor2, never()).compensate("target2"); // step2 failed, so it shouldn't be compensated
    }

    @Test
    void shouldCompensateInReverseOrder() {
        when(executor1.execute(anyString())).thenReturn(true);
        when(executor2.execute(anyString())).thenReturn(true);
        ActionExecutor executor3 = mock(ActionExecutor.class);
        when(executor3.execute(anyString())).thenReturn(false);

        sagaEngine.addStep(new SagaEngine.Step("step1", executor1, "target1"));
        sagaEngine.addStep(new SagaEngine.Step("step2", executor2, "target2"));
        sagaEngine.addStep(new SagaEngine.Step("step3", executor3, "target3"));

        sagaEngine.run();

        var inOrder = Mockito.inOrder(executor1, executor2, executor3);
        inOrder.verify(executor1).execute("target1");
        inOrder.verify(executor2).execute("target2");
        inOrder.verify(executor3).execute("target3");
        inOrder.verify(executor2).compensate("target2");
        inOrder.verify(executor1).compensate("target1");
    }
}
