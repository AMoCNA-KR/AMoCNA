import { useState } from 'react';
import KpiRow from '../../components/KpiRow';
import MapeLoop from '../../components/MapeLoop';
import Toast from '../../components/Toast';
import { useTelemetry } from '../../hooks/useTelemetry';
import { useStore } from '../../lib/store';
import styles from './DashboardView.module.css';

export default function DashboardView() {
  const { setIsSimulating, setActiveNode } = useStore();
  const [toast, setToast] = useState({ show: false, title: '', body: '' });

  const showNotification = (title: string, body: string) => {
    setToast({ show: true, title, body });
    setTimeout(() => {
      setToast(prev => ({ ...prev, show: false }));
    }, 3500);
  };

  const {
    activeAnomalies,
    totalInterventions,
    avgLatency,
    successRate,
    appendTerminalLine,
    setActiveAnomalies,
    setTotalInterventions
  } = useTelemetry();

  // Simulated breach execution trigger
  const triggerMockViolation = () => {
    setIsSimulating(false);
    setActiveNode('monitor');
    setActiveAnomalies(1);
    
    appendTerminalLine('monitor', '⚠️ Anomaly Detected: frontend-latency.yml breached limit 0.85s (Value: 0.97s)');
    appendTerminalLine('system', 'SPARQL Query inserted: ResponseTimeSlaViolatedState for service front-end');
    appendTerminalLine('monitor', 'Published GraphUpdateMessage to queue amocna.graph.updates');
    showNotification('Anomaly Detected!', 'Service response latency SLA breached. Executing MAPE-K loop...');

    setTimeout(() => {
      setActiveNode('analyze');
      appendTerminalLine('analyze', 'Palamedes captured GraphUpdateMessage. AnomalyAgent reasoning active.');
      appendTerminalLine('analyze', 'Ontology matched Anomaly ➔ Service front-end. Recommended SCALE_UP.');
    }, 3000);

    setTimeout(() => {
      setActiveNode('plan');
      appendTerminalLine('analyze', 'Planner evaluated policies. Created ActionMessage: act-scale-up-12');
    }, 6000);

    setTimeout(() => {
      setActiveNode('execute');
      appendTerminalLine('execute', 'Themis ActionQueueListener popped act-scale-up-12. Evaluating pre-conditions...');
      appendTerminalLine('execute', 'Stateless HTTP scaling invocation dispatched. Pod scaled 1 ➔ 2.');
      appendTerminalLine('execute', 'Post-conditions satisfied. Published ActionStatusUpdate COMPLETED.');
    }, 9000);

    setTimeout(() => {
      setActiveNode('knowledge');
      appendTerminalLine('system', 'SagaManager received status update. Removed ResponseTimeSlaViolatedState from GraphDB.');
      setActiveAnomalies(0);
      setTotalInterventions(prev => prev + 1);
      showNotification('Cluster Restored!', 'The autonomic loop completed successfully. SLA anomaly resolved.');
      setIsSimulating(true);
    }, 12000);
  };

  return (
    <>
      <KpiRow 
        avgLatency={avgLatency}
        successRate={successRate}
        activeAnomalies={activeAnomalies}
        totalInterventions={totalInterventions}
      />

      <div className={styles.workspaceGrid}>
        <div className={styles.visualizerColumn}>
          <MapeLoop triggerMockViolation={triggerMockViolation} />
        </div>
      </div>

      <Toast 
        show={toast.show}
        title={toast.title}
        body={toast.body}
      />
    </>
  );
}
