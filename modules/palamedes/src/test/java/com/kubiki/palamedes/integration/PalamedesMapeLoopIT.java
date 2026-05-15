package com.kubiki.palamedes.integration;

import com.kubiki.palamedes.knowledge.OntologyRegistry;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionMessage;
import com.kubiki.palamedes.model.ActionStatusUpdate;
import com.kubiki.palamedes.model.ExecutionStatus;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.pipeline.MapePipeline;
import com.kubiki.palamedes.saga.SagaManager;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PalamedesMapeLoopIT {

    @Autowired
    private MapePipeline mapePipeline;

    @Autowired
    private SagaManager sagaManager;

    @Autowired
    private GraphDBGateway gateway;

    @Autowired
    private OntologyRegistry registry;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private Repository realRepository;

    private static Repository inMemoryRepo;
    private final ValueFactory vf = SimpleValueFactory.getInstance();

    @BeforeEach
    void setUp() {
        if (inMemoryRepo == null) {
            inMemoryRepo = new SailRepository(new MemoryStore());
            inMemoryRepo.init();
        }
        clearGraph();
        
        try {
            when(realRepository.getConnection()).thenAnswer(inv -> inMemoryRepo.getConnection());
            when(realRepository.getValueFactory()).thenReturn(vf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void clearGraph() {
        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            conn.clear();
            conn.commit();
        }
    }

    @Test
    void shouldExecuteFullMapeLoop() {
        clearGraph();
        IRI pod = vf.createIRI("http://test/pod1");
        IRI state = vf.createIRI("http://test/pod1/state");
        IRI anomalyType = vf.createIRI("http://test/Anomaly1");
        IRI intent = vf.createIRI("http://test/RestartAction");
        
        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.add(pod, registry.cnee("hasCurrentState"), state);
            conn.add(state, RDF.TYPE, anomalyType);
            conn.add(pod, registry.cnee("resourceName"), vf.createLiteral("pod1"));
            
            IRI restriction = vf.createIRI("http://test/rest1");
            conn.add(anomalyType, org.eclipse.rdf4j.model.vocabulary.RDFS.SUBCLASSOF, restriction);
            conn.add(restriction, RDF.TYPE, org.eclipse.rdf4j.model.vocabulary.OWL.RESTRICTION);
            conn.add(restriction, org.eclipse.rdf4j.model.vocabulary.OWL.ONPROPERTY, registry.bridge("isResolvedByIntent"));
            conn.add(restriction, org.eclipse.rdf4j.model.vocabulary.OWL.SOMEVALUESFROM, intent);
            
            conn.add(intent, RDF.TYPE, registry.moam("SimpleAction"));
            conn.add(intent, registry.moam("hasExecutionProtocol"), vf.createLiteral("REST"));
            conn.add(intent, registry.moam("hasExecutionInstruction"), vf.createLiteral("http://restart/{resourceName}"));
            conn.add(intent, registry.moam("hasExpectedStatusCode"), vf.createLiteral("200", org.eclipse.rdf4j.model.vocabulary.XSD.INTEGER));
        }

        mapePipeline.run(); 
        
        List<GraphDBGateway.ActiveActionSummary> active = gateway.findActiveActions();
        assertEquals(1, active.size());
        IRI actionIri = active.get(0).actionIri();

        mapePipeline.run();
        active = gateway.findActiveActions();
        assertEquals("State_Validated", active.get(0).stateFragment());

        mapePipeline.run();
        active = gateway.findActiveActions();
        assertEquals("State_InProgress", active.get(0).stateFragment());
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(anyString(), anyString(), any(ActionMessage.class));

        sagaManager.handleFeedback(new ActionStatusUpdate(actionIri.getLocalName(), ExecutionStatus.COMPLETED, null, 200));
        assertTrue(gateway.findActiveActions().isEmpty());
    }

    @Test
    void shouldDecomposeComplexWorkflow() {
        clearGraph();
        IRI pod = vf.createIRI("http://test/pod3");
        IRI state = vf.createIRI("http://test/pod3/state");
        IRI anomalyType = vf.createIRI("http://test/ScaleAnomaly");
        IRI intent = vf.createIRI("http://test/ScalingComplexWorkflow");
        
        IRI step1 = vf.createIRI("http://test/Step1");
        IRI step2 = vf.createIRI("http://test/Step2");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.add(pod, registry.cnee("hasCurrentState"), state);
            conn.add(state, RDF.TYPE, anomalyType);
            conn.add(pod, registry.cnee("resourceName"), vf.createLiteral("pod3"));
            
            IRI restriction = vf.createIRI("http://test/rest3");
            conn.add(anomalyType, org.eclipse.rdf4j.model.vocabulary.RDFS.SUBCLASSOF, restriction);
            conn.add(restriction, RDF.TYPE, org.eclipse.rdf4j.model.vocabulary.OWL.RESTRICTION);
            conn.add(restriction, org.eclipse.rdf4j.model.vocabulary.OWL.ONPROPERTY, registry.bridge("isResolvedByIntent"));
            conn.add(restriction, org.eclipse.rdf4j.model.vocabulary.OWL.SOMEVALUESFROM, intent);
            
            conn.add(intent, RDF.TYPE, registry.moam("ComplexWorkflow"));
            conn.add(intent, registry.moam("isDecomposedInto"), step1);
            conn.add(intent, registry.moam("isDecomposedInto"), step2);
            
            conn.add(step1, RDF.TYPE, registry.moam("SimpleAction"));
            conn.add(step1, registry.moam("hasExecutionProtocol"), vf.createLiteral("REST"));
            conn.add(step1, registry.moam("hasExecutionInstruction"), vf.createLiteral("http://step1"));
            conn.add(step1, registry.moam("hasExpectedStatusCode"), vf.createLiteral("200", org.eclipse.rdf4j.model.vocabulary.XSD.INTEGER));

            conn.add(step2, RDF.TYPE, registry.moam("SimpleAction"));
            conn.add(step2, registry.moam("hasExecutionProtocol"), vf.createLiteral("REST"));
            conn.add(step2, registry.moam("hasExecutionInstruction"), vf.createLiteral("http://step2"));
            conn.add(step2, registry.moam("hasExpectedStatusCode"), vf.createLiteral("200", org.eclipse.rdf4j.model.vocabulary.XSD.INTEGER));
        }

        // ONE TICK: Analyze + Parent Plan
        mapePipeline.run(); 
        
        List<GraphDBGateway.ActiveActionSummary> active = gateway.findActiveActions();

        assertTrue(active.size() >= 2, "Should have parent and children active. Found: " + active.size());
        
        final List<GraphDBGateway.ActiveActionSummary> activeActionsAfterPlan = active;
        IRI child1Iri = activeActionsAfterPlan.stream()
            .filter(a -> a.stateFragment().equals("State_Initial"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Child 1 not found in INITIAL state in: " + activeActionsAfterPlan))
            .actionIri();
        
        // Tick Child 1 to InProgress
        mapePipeline.run(); // Child 1: Planned
        mapePipeline.run(); // Child 1: Validated
        mapePipeline.run(); // Child 1: InProgress
        
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(anyString(), anyString(), any(ActionMessage.class));

        sagaManager.handleFeedback(new ActionStatusUpdate(child1Iri.getLocalName(), ExecutionStatus.COMPLETED, null, 200));
        
        // SagaManager unlocks Step 2.
        // Next run will see Step 2 in INITIAL and move it to PLANNED.
        mapePipeline.run();
        
        active = gateway.findActiveActions();
        assertTrue(active.stream().anyMatch(a -> a.stateFragment().equals("State_Planned")), "Next step should have advanced to PLANNED");
    }
}
