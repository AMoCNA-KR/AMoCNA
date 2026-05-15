package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.KubernetesSensor;
import com.kubiki.metis.sensor.SensorEventPublisher;
import com.kubiki.metis.grpc.*;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Watches Pods and Services to detect and emit topology relationships:
 * <ul>
 *   <li>{@code cnee:contains(service, pod)} — when a pod's labels match a service's selector</li>
 *   <li>{@code cnee:isHostedOn(pod, node)} — when a pod is scheduled to a node</li>
 * </ul>
 *
 * <p>Uses {@code cnee:contains} / {@code cnee:isHostedOn} predicates which trigger
 * automatic inverse triple insertion in {@link com.kubiki.metis.knowledge.KnowledgeBaseWriter}.
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class BindingSensor implements KubernetesSensor {

    private static final Logger log = LoggerFactory.getLogger(BindingSensor.class);

    private static final String CNEE_CONTAINS      = "contains";
    private static final String CNEE_IS_HOSTED_ON  = "isHostedOn";

    private final KubernetesClient client;
    private final MetisProperties properties;
    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;
    private final String cneeNamespace;

    private final List<SharedIndexInformer<?>> informers = new ArrayList<>();

    public BindingSensor(KubernetesClient client,
                         MetisProperties properties,
                         SensorEventPublisher publisher,
                         IriFactory iriFactory) {
        this.client = client;
        this.properties = properties;
        this.publisher = publisher;
        this.iriFactory = iriFactory;
        this.cneeNamespace = properties.ontology().cneeNamespace();
    }

    @Override
    public String name() {
        return "BindingSensor";
    }

    @Override
    public void start() {
        List<String> namespaces = properties.sensor() != null
                ? properties.sensor().namespaces()
                : List.of();

        if (namespaces.isEmpty()) {
            startForNamespace(null);
            log.info("BindingSensor watching all namespaces");
        } else {
            for (String ns : namespaces) {
                startForNamespace(ns);
            }
            log.info("BindingSensor watching namespaces: {}", namespaces);
        }
    }

    @Override
    public void stop() {
        for (SharedIndexInformer<?> informer : informers) {
            try { informer.stop(); } catch (Exception e) {
                log.warn("BindingSensor error stopping informer: {}", e.getMessage());
            }
        }
        informers.clear();
        log.info("BindingSensor stopped");
    }

    // -------------------------------------------------------------------------

    private void startForNamespace(String namespace) {
        var podOp = namespace != null
                ? client.pods().inNamespace(namespace)
                : client.pods().inAnyNamespace();

        var svcOp = namespace != null
                ? client.services().inNamespace(namespace)
                : client.services().inAnyNamespace();

        // Watch pods — emit pod↔node and pod↔service bindings
        SharedIndexInformer<Pod> podInformer = podOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Pod pod) {
                emitPodNodeBinding(pod);
                emitPodServiceBindings(pod, namespace);
            }

            @Override
            public void onUpdate(Pod oldPod, Pod newPod) {
                // Re-emit if node assignment changed (pod rescheduled)
                String oldNode = nodeName(oldPod);
                String newNode = nodeName(newPod);
                if (newNode != null && !newNode.equals(oldNode)) {
                    emitPodNodeBinding(newPod);
                }
            }

            @Override
            public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
                // Deletion is handled by PodSensor — KnowledgeBaseWriter removes all triples
            }
        });

        podInformer.start();
        informers.add(podInformer);

        // Watch services — emit service↔pod bindings for existing pods
        SharedIndexInformer<Service> svcInformer = svcOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Service svc) {
                emitServicePodBindings(svc, namespace);
            }

            @Override
            public void onUpdate(Service oldSvc, Service newSvc) {
                // Re-emit if selector changed
                if (!selectorsEqual(oldSvc, newSvc)) {
                    emitServicePodBindings(newSvc, namespace);
                }
            }

            @Override
            public void onDelete(Service svc, boolean deletedFinalStateUnknown) {
                // Deletion handled by ServiceSensor
            }
        });

        svcInformer.start();
        informers.add(svcInformer);
    }

    // -------------------------------------------------------------------------
    // Pod → Node

    private void emitPodNodeBinding(Pod pod) {
        String node = nodeName(pod);
        if (node == null || node.isBlank()) return;

        String ns      = pod.getMetadata().getNamespace();
        String podName = pod.getMetadata().getName();
        String podIri  = iriFactory.namespacedIri("Pod", ns, podName);
        String nodeIri = iriFactory.clusterScopedIri("Node", node);

        emitRelationship(podIri, CNEE_IS_HOSTED_ON, nodeIri);
        log.debug("BindingSensor: pod {}/{} isHostedOn node {}", ns, podName, node);
    }

    // -------------------------------------------------------------------------
    // Pod → Services (check all services in namespace for matching selector)

    private void emitPodServiceBindings(Pod pod, String namespace) {
        String ns      = pod.getMetadata().getNamespace();
        String podName = pod.getMetadata().getName();
        Map<String, String> podLabels = pod.getMetadata().getLabels();
        if (podLabels == null || podLabels.isEmpty()) return;

        List<Service> services = namespace != null
                ? client.services().inNamespace(ns).list().getItems()
                : client.services().inNamespace(ns).list().getItems();

        for (Service svc : services) {
            if (selectorMatches(svc, podLabels)) {
                String svcIri = iriFactory.namespacedIri("Service", ns, svc.getMetadata().getName());
                String podIri = iriFactory.namespacedIri("Pod", ns, podName);
                emitRelationship(svcIri, CNEE_CONTAINS, podIri);
                log.debug("BindingSensor: service {}/{} contains pod {}", ns, svc.getMetadata().getName(), podName);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Service → Pods (check all pods in namespace for matching labels)

    private void emitServicePodBindings(Service svc, String namespace) {
        Map<String, String> selector = selector(svc);
        if (selector == null || selector.isEmpty()) return;

        String ns      = svc.getMetadata().getNamespace();
        String svcName = svc.getMetadata().getName();
        String svcIri  = iriFactory.namespacedIri("Service", ns, svcName);

        List<Pod> pods = client.pods().inNamespace(ns).list().getItems();
        for (Pod pod : pods) {
            Map<String, String> podLabels = pod.getMetadata().getLabels();
            if (podLabels != null && podLabels.entrySet().containsAll(selector.entrySet())) {
                String podIri = iriFactory.namespacedIri("Pod", ns, pod.getMetadata().getName());
                emitRelationship(svcIri, CNEE_CONTAINS, podIri);
                log.debug("BindingSensor: service {}/{} contains pod {}", ns, svcName, pod.getMetadata().getName());
            }
        }
    }

    // -------------------------------------------------------------------------

    private void emitRelationship(String subjectIri, String predicateLocalName, String objectIri) {
        RelationshipAssertedEvent rel = RelationshipAssertedEvent.newBuilder()
                .setSubjectIri(subjectIri)
                .setPredicate(cneeNamespace + predicateLocalName)
                .setObjectIri(objectIri)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setRelationshipAsserted(rel)));
    }

    private static String nodeName(Pod pod) {
        if (pod.getSpec() == null) return null;
        return pod.getSpec().getNodeName();
    }

    private static Map<String, String> selector(Service svc) {
        if (svc.getSpec() == null) return null;
        return svc.getSpec().getSelector();
    }

    private static boolean selectorMatches(Service svc, Map<String, String> podLabels) {
        Map<String, String> sel = selector(svc);
        if (sel == null || sel.isEmpty()) return false;
        return podLabels.entrySet().containsAll(sel.entrySet());
    }

    private static boolean selectorsEqual(Service a, Service b) {
        Map<String, String> sa = selector(a);
        Map<String, String> sb = selector(b);
        if (sa == null && sb == null) return true;
        if (sa == null || sb == null) return false;
        return sa.equals(sb);
    }
}
