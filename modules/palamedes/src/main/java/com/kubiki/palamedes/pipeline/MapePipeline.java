package com.kubiki.palamedes.pipeline;

import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActiveActionSummary;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class MapePipeline {
    private static final Logger log = LoggerFactory.getLogger(MapePipeline.class);
    private final List<MapePipe> pipes;
    private final GraphDBGateway graphDBGateway;
    private final PalamedesProperties palamedesProperties;


    @EventListener(EngineWakeupEvent.class)
    @Scheduled(fixedRateString = "${palamedes.engine.pipeline-rate-ms}")
    public void run() {
        log.debug("Starting MAPE Pipeline run...");

        List<ActiveActionSummary> activeActions = graphDBGateway.findActiveActions();
        if (activeActions.isEmpty()) {
            return;
        }
        log.debug("Found {} active actions in the Petri Net", activeActions.size());

        int batchSize = palamedesProperties.engine().batchSize();
        List<List<ActiveActionSummary>> batches = partition(activeActions, batchSize);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<ActiveActionSummary> batch : batches) {
                executor.submit(() -> processBatch(batch));
            }
        } catch (Exception e) {
            log.error("Error in MAPE Pipeline virtual thread execution: {}", e.getMessage(), e);
        }
    }

    private void processBatch(List<ActiveActionSummary> batch) {
        List<IRI> iris = batch.stream().map(ActiveActionSummary::actionIri).toList();
        Map<IRI, ActionData> structures = graphDBGateway.fetchActionStructures(iris);

        for (ActiveActionSummary action : batch) {
            try {
                ActionData data = structures.get(action.actionIri());
                if (data == null) {
                    log.warn("Could not load structure for action {}, skipping", action.actionIri());
                    continue;
                }

                WorkflowContext context = new WorkflowContext(action.actionIri(), data);
                context.metadata().put("resourceName", action.resourceName());
                context.metadata().put("currentState", action.stateFragment());

                for (MapePipe pipe : pipes) {
                    if (!pipe.process(context)) {
                        log.debug("Pipeline stopped at {} for action {}", pipe.getClass().getSimpleName(), action.actionIri());
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error processing action {} in pipeline: {}", action.actionIri(), e.getMessage(), e);
            }
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
