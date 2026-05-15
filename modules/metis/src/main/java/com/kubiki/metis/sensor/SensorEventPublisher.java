package com.kubiki.metis.sensor;

import com.google.protobuf.Timestamp;
import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.IngestResponse;
import com.kubiki.metis.grpc.SensorBatch;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.SensorEventProcessor;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.ingestion.model.ProcessResult;
import com.kubiki.metis.notification.PalamedesNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffers {@link SensorEvent}s produced by {@link KubernetesSensor} implementations
 * and flushes them as {@link SensorBatch}es directly to the in-process
 * {@link SensorEventProcessor} — no network hop required.
 *
 * <p>A batch is flushed when either:
 * <ul>
 *   <li>the buffer reaches {@code metis.sensor.batch-size} events, or</li>
 *   <li>{@code metis.sensor.flush-interval-ms} milliseconds have elapsed.</li>
 * </ul>
 */
@Component
public class SensorEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SensorEventPublisher.class);

    private final SensorEventProcessor processor;
    private final PalamedesNotifier notifier;
    private final int batchSize;
    private final long flushIntervalMs;
    private final String sensorHostname;

    private final List<SensorEvent> buffer = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private ScheduledExecutorService scheduler;

    public SensorEventPublisher(SensorEventProcessor processor,
                                PalamedesNotifier notifier,
                                MetisProperties properties) {
        this.processor = processor;
        this.notifier = notifier;
        this.batchSize = properties.sensor().batchSize();
        this.flushIntervalMs = properties.sensor().flushIntervalMs();
        this.sensorHostname = resolveHostname();
    }

    /**
     * Start the periodic flush scheduler. Called by {@link SensorOrchestrator}.
     */
    void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sensor-flush");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        log.info("SensorEventPublisher started [batchSize={}, flushIntervalMs={}]", batchSize, flushIntervalMs);
    }

    /**
     * Stop the scheduler and flush any remaining buffered events.
     */
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        flush();
        log.info("SensorEventPublisher stopped");
    }

    /**
     * Enqueue a sensor event. Triggers an immediate flush if the buffer is full.
     *
     * @param event the event to publish; must not be {@code null}
     */
    public void publish(SensorEvent event) {
        lock.lock();
        try {
            buffer.add(event);
            if (buffer.size() >= batchSize) {
                flushUnderLock();
            }
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------------------------

    private void flush() {
        lock.lock();
        try {
            flushUnderLock();
        } finally {
            lock.unlock();
        }
    }

    /** Must be called with {@link #lock} held. */
    private void flushUnderLock() {
        if (buffer.isEmpty()) return;

        List<SensorEvent> batch = new ArrayList<>(buffer);
        buffer.clear();

        String correlationId = buildCorrelationId();
        SensorBatch sensorBatch = SensorBatch.newBuilder()
                .setCorrelationId(correlationId)
                .addAllEvents(batch)
                .build();

        // In-process call — no gRPC network overhead
        try {
            ProcessResult result = processor.processBatch(sensorBatch.getEventsList(), correlationId);

            HandlerResult firstSuccess = result.firstSuccess();
            if (firstSuccess != null) {
                notifier.notify(
                        firstSuccess.resourceIri(),
                        firstSuccess.ontologyType(),
                        firstSuccess.changeKind(),
                        correlationId);
            }

            log.debug("Sensor batch flushed [correlationId={}, events={}, processed={}, failed={}]",
                    correlationId, batch.size(), result.processedCount(), result.failedCount());

            if (!result.failureMessages().isEmpty()) {
                log.warn("Sensor batch had failures [correlationId={}]: {}",
                        correlationId, String.join("; ", result.failureMessages()));
            }
        } catch (Exception e) {
            log.error("Failed to flush sensor batch [correlationId={}]: {}", correlationId, e.getMessage(), e);
        }
    }

    private String buildCorrelationId() {
        // Format: <hostname>-<uuid-short> — guaranteed ≤ 128 chars
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String raw = sensorHostname + "-" + uuid;
        return raw.length() > 128 ? raw.substring(0, 128) : raw;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "metis-sensor";
        }
    }

    /**
     * Wraps a {@link SensorEvent} with the current timestamp.
     */
    public static SensorEvent withTimestamp(SensorEvent.Builder builder) {
        Instant now = Instant.now();
        return builder
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .build();
    }
}
