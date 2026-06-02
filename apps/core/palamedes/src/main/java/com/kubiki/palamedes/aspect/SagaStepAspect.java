package com.kubiki.palamedes.aspect;

import com.kubiki.common.logging.SagaStep;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class SagaStepAspect {

    private static final Logger log = LoggerFactory.getLogger(SagaStepAspect.class);

    @Around("@annotation(sagaStep)")
    public Object manageSagaStep(ProceedingJoinPoint joinPoint, SagaStep sagaStep) throws Throwable {
        log.info("SagaStep Aspect: Intercepting step: '{}'", sagaStep.name());
        Object result = null;
        try {
            result = joinPoint.proceed();
            
            if (result != null) {
                try {
                    Method successMethod = result.getClass().getMethod("success");
                    boolean success = (boolean) successMethod.invoke(result);
                    if (!success) {
                        log.warn("SagaStep Aspect: Step '{}' returned a failure result. Triggering compensation: '{}'", 
                                sagaStep.name(), sagaStep.compensationMethod());
                        invokeCompensation(joinPoint, sagaStep.compensationMethod());
                    }
                } catch (NoSuchMethodException e) {
                    // Result doesn't have a success method, assume success since no exception was thrown
                }
            }
            return result;
        } catch (Throwable t) {
            log.error("SagaStep Aspect: Step '{}' failed with exception: {}. Triggering compensation: '{}'", 
                    sagaStep.name(), t.getMessage(), sagaStep.compensationMethod());
            invokeCompensation(joinPoint, sagaStep.compensationMethod());
            throw t;
        }
    }

    private void invokeCompensation(ProceedingJoinPoint joinPoint, String compensationMethodName) {
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?>[] parameterTypes = signature.getParameterTypes();

        try {
            Method compensationMethod = target.getClass().getMethod(compensationMethodName, parameterTypes);
            compensationMethod.setAccessible(true);
            log.info("SagaStep Aspect: Invoking compensation method '{}' on target '{}'", compensationMethodName, target.getClass().getSimpleName());
            compensationMethod.invoke(target, args);
        } catch (Exception e) {
            log.error("SagaStep Aspect: Failed to invoke compensation method '{}': {}", compensationMethodName, e.getMessage(), e);
        }
    }
}
