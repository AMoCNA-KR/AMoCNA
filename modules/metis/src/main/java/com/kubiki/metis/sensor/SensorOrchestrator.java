package com.kubiki.metis.sensor;

import com.kubiki.metis.config.MetisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manages the lifecycle of all {@link KubernetesSensor} beans.
 *
 * <p>Starts sensors after the application context is fully ready (gRPC server up,
 * all beans initialized) and stops them cleanly on shutdown.
 *
 * <p>If {@code metis.sensor.enabled} is {@code false}, no sensors are started.
 */
@Component
public class SensorOrchestrator implements ApplicationListener<ApplicationReadyEvent>, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SensorOrchestrator.class);

    private final List<KubernetesSensor> sensors;
    private final SensorEventPublisher publisher;
    private final boolean enabled;

    private volatile boolean running = false;

    public SensorOrchestrator(List<KubernetesSensor> sensors,
                               SensorEventPublisher publisher,
                               MetisProperties properties) {
        this.sensors = sensors;
        this.publisher = publisher;
        this.enabled = properties.sensor() != null && properties.sensor().enabled();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!enabled) {
            log.info("Kubernetes sensor layer is disabled (metis.sensor.enabled=false)");
            return;
        }
        if (sensors.isEmpty()) {
            log.info("No KubernetesSensor beans found — sensor layer idle");
            return;
        }

        log.info("Starting Kubernetes sensor layer [{} sensor(s)]", sensors.size());
        publisher.startScheduler();

        for (KubernetesSensor sensor : sensors) {
            try {
                log.info("Starting sensor: {}", sensor.name());
                sensor.start();
            } catch (Exception e) {
                log.error("Failed to start sensor '{}': {}", sensor.name(), e.getMessage(), e);
            }
        }

        running = true;
    }

    // SmartLifecycle — called on context close / SIGTERM

    @Override
    public void start() {
        // Actual start happens in onApplicationEvent to ensure gRPC server is up first
    }

    @Override
    public void stop() {
        if (!running) return;
        log.info("Stopping Kubernetes sensor layer");

        for (KubernetesSensor sensor : sensors) {
            try {
                log.info("Stopping sensor: {}", sensor.name());
                sensor.stop();
            } catch (Exception e) {
                log.error("Error stopping sensor '{}': {}", sensor.name(), e.getMessage(), e);
            }
        }

        publisher.stopScheduler();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Stop before gRPC server (default phase 0) so no events arrive after shutdown
        return Integer.MAX_VALUE - 1;
    }
}
