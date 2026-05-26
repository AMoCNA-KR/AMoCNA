package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import com.kubiki.metis.grpc.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Watches Deployments and StatefulSets to map Workload Controllers.
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class WorkloadSensor extends AbstractNamespacedSensor {

    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;

    public WorkloadSensor(KubernetesClient client,
                          MetisProperties properties,
                          SensorEventPublisher publisher,
                          IriFactory iriFactory) {
        super(client, properties);
        this.publisher = publisher;
        this.iriFactory = iriFactory;
    }

    @Override
    public String name() {
        return "WorkloadSensor";
    }

    @Override
    protected SharedIndexInformer<?> createInformer(KubernetesClient client, String namespace) {
        // This is a bit tricky since AbstractNamespacedSensor expects one informer per sensor.
        // We'll manage the lifecycle of both informers manually or override start/stop.
        // For simplicity here, I'll return the Deployment informer and start StatefulSet separately.
        
        var ssOp = namespace != null
                ? client.apps().statefulSets().inNamespace(namespace)
                : client.apps().statefulSets().inAnyNamespace();
        
        ssOp.inform(new ResourceEventHandler<>() {
            @Override public void onAdd(StatefulSet ss) { onStatefulSetAdded(ss); }
            @Override public void onUpdate(StatefulSet old, StatefulSet next) {}
            @Override public void onDelete(StatefulSet ss, boolean unknown) { onStatefulSetDeleted(ss); }
        });

        var depOp = namespace != null
                ? client.apps().deployments().inNamespace(namespace)
                : client.apps().deployments().inAnyNamespace();

        return depOp.inform(new ResourceEventHandler<>() {
            @Override public void onAdd(Deployment dep) { onDeploymentAdded(dep); }
            @Override public void onUpdate(Deployment old, Deployment next) {}
            @Override public void onDelete(Deployment dep, boolean unknown) { onDeploymentDeleted(dep); }
        });
    }

    private void onDeploymentAdded(Deployment dep) {
        emitDiscovery(CneeOntology.KIND_DEPLOYMENT, CneeOntology.CLASS_STATELESS_CONTROLLER, dep.getMetadata().getNamespace(), dep.getMetadata().getName());
    }

    private void onDeploymentDeleted(Deployment dep) {
        emitDeletion(CneeOntology.KIND_DEPLOYMENT, CneeOntology.CLASS_STATELESS_CONTROLLER, dep.getMetadata().getNamespace(), dep.getMetadata().getName());
    }

    private void onStatefulSetAdded(StatefulSet ss) {
        emitDiscovery(CneeOntology.KIND_STATEFULSET, CneeOntology.CLASS_STATEFUL_CONTROLLER, ss.getMetadata().getNamespace(), ss.getMetadata().getName());
    }

    private void onStatefulSetDeleted(StatefulSet ss) {
        emitDeletion(CneeOntology.KIND_STATEFULSET, CneeOntology.CLASS_STATEFUL_CONTROLLER, ss.getMetadata().getNamespace(), ss.getMetadata().getName());
    }

    private void emitDiscovery(String kind, String typeLocal, String ns, String name) {
        String iri = iriFactory.namespacedIri(kind, ns, name);
        String type = iriFactory.typeIri(typeLocal);

        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(type)
                .setResourceId(name)
                .setResourceName(name)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));
    }

    private void emitDeletion(String kind, String typeLocal, String ns, String name) {
        String iri = iriFactory.namespacedIri(kind, ns, name);
        EntityDeletedEvent deleted = EntityDeletedEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(iriFactory.typeIri(typeLocal))
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDeleted(deleted)));
    }
}
