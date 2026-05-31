import { useState, useEffect } from 'react';
import KpiRow from '../../components/KpiRow';
import MapeLoop from '../../components/MapeLoop';
import TelemetryChart from '../../components/TelemetryChart';
import EventTerminal from '../../components/EventTerminal';
import Toast from '../../components/Toast';
import { useTelemetry } from '../../hooks/useTelemetry';
import { useStore } from '../../lib/store';

export default function DashboardView() {
  const { activeNode, isSimulating, setIsSimulating, setActiveNode } = useStore();
  const [chartData, setChartData] = useState<{ time: string; latency: number; cpu: number }[]>([]);
  const [toast, setToast] = useState({ show: false, title: '', body: '' });

  const showNotification = (title: string, body: string) => {
    setToast({ show: true, title, body });
    setTimeout(() => {
      setToast(prev => ({ ...prev, show: false }));
    }, 3500);
  };

  const {
    logs,
    activeAnomalies,
    totalInterventions,
    avgLatency,
    successRate,
    appendTerminalLine,
    setActiveAnomalies,
    setTotalInterventions
  } = useTelemetry();

  useEffect(() => {
    const initialData = [];
    const now = new Date();
    for (let i = 11; i >= 0; i--) {
      const d = new Date(now.getTime() - i * 1000);
      const timeStr = d.toTimeString().split(' ')[0];
      initialData.push({
        time: timeStr,
        latency: parseFloat((0.35 + Math.random() * 0.1).toFixed(3)),
        cpu: parseFloat((25 + Math.random() * 8).toFixed(1))
      });
    }
    setChartData(initialData);

    const interval = setInterval(() => {
      setChartData(prev => {
        const nextTime = new Date().toTimeString().split(' ')[0];
        
        let newLatency = 0.35 + Math.random() * 0.1;
        let newCpu = 25 + Math.random() * 8;
        
        if (!isSimulating && activeNode !== 'knowledge') {
          newLatency = 0.92 + Math.random() * 0.08;
          newCpu = 88 + Math.random() * 6;
        }

        const nextPoints = [
          ...prev.slice(1), 
          { 
            time: nextTime, 
            latency: parseFloat(newLatency.toFixed(3)), 
            cpu: parseFloat(newCpu.toFixed(1)) 
          }
        ];
        return nextPoints;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [isSimulating, activeNode]);

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

      <div className="workspace-grid" style={{ gridTemplateColumns: '1fr' }}>
        <div className="visualizer-column">
          <MapeLoop triggerMockViolation={triggerMockViolation} />
          <TelemetryChart data={chartData} />
          <EventTerminal logs={logs} />
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
