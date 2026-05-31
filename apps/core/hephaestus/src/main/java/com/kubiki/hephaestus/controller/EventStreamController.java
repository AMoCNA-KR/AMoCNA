package com.kubiki.hephaestus.controller;

import com.kubiki.hephaestus.service.EventStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class EventStreamController {

    private final EventStreamService eventStreamService;
    private final RestClient.Builder restClientBuilder;

    @Value("${amocna.metrics-adapter.url:http://localhost:8085}")
    private String metricsAdapterUrl;

    @Value("${amocna.graphdb.url:http://localhost:7200}")
    private String graphdbUrl;

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitmqHost;

    /**
     * Exposes the active telemetry stream as a Server-Sent Events (SSE) resource.
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        log.info("Client connected to Hephaestus Server-Sent Events stream.");
        // Set a generous timeout (5 minutes)
        SseEmitter emitter = new SseEmitter(300_000L);
        eventStreamService.register(emitter);
        return emitter;
    }

    /**
     * Dynamically pings all AMoCNA core services and returns their active health status.
     * Automatically adapts connection endpoints depending on whether the system is hosted
     * locally or running inside a Kubernetes cluster environment.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> getServicesHealth() {
        RestClient client = restClientBuilder.build();
        Map<String, String> health = new HashMap<>();

        // Detect if we are running in Kubernetes cluster by checking internal DNS resolution
        boolean isK8s = false;
        try {
            InetAddress.getByName("kubernetes.default.svc.cluster.local");
            isK8s = true;
        } catch (Exception e) {
            // Not in k8s or dns not resolvable
        }

        // 1. Themis
        String themisUrl = isK8s ? "http://themis.themis.svc.cluster.local:8080/actuator/health" : "http://localhost:8080/actuator/health";
        health.put("themis", pingService(client, themisUrl));

        // 2. Palamedes
        String palamedesUrl = isK8s ? "http://palamedes.palamedes.svc.cluster.local:8081/actuator/health" : "http://localhost:8081/actuator/health";
        health.put("palamedes", pingService(client, palamedesUrl));

        // 3. Metis
        String metisUrl = isK8s ? "http://metis.metis.svc.cluster.local:8080/actuator/health" : "http://localhost:50052";
        health.put("metis", pingService(client, metisUrl));

        // 4. Metrics Adapter
        String maUrl = metricsAdapterUrl + "/actuator/health";
        health.put("metrics-adapter", pingService(client, maUrl));

        // 5. GraphDB
        String gdbUrl = graphdbUrl + "/protocol";
        health.put("graphdb", pingService(client, gdbUrl));

        // 6. RabbitMQ
        String rmHost = rabbitmqHost;
        String rabbitUrl = isK8s ? "http://rabbitmq.rabbitmq.svc.cluster.local:15672/" : "http://" + rmHost + ":15672/";
        health.put("rabbitmq", pingService(client, rabbitUrl));

        return ResponseEntity.ok(health);
    }

    private String pingService(RestClient client, String url) {
        try {
            // Probe service health with quick connection timeout
            client.get()
                  .uri(URI.create(url))
                  .retrieve()
                  .toBodilessEntity();
            return "UP";
        } catch (Exception e) {
            // For metis gRPC port 50052 locally, fetching it throws a bad request or protocol error, but means socket is alive!
            if (url.contains("50052") && !e.getMessage().contains("Connection refused") && !e.getMessage().contains("Timeout")) {
                return "UP";
            }
            log.debug("Health probe failed for {}: {}", url, e.getMessage());
            return "DOWN";
        }
    }
}
