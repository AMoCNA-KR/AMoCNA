import { useEffect } from 'react';
import { useStore } from '../lib/store';

export function useTelemetry() {
  const {
    logs,
    activeAnomalies,
    totalInterventions,
    avgLatency,
    successRate,
    setActiveNode,
    setActiveAnomalies,
    setTotalInterventions,
    appendTerminalLine,
  } = useStore();

  // Setup EventSource subscription
  useEffect(() => {
    if (logs.length === 0) {
      appendTerminalLine('system', 'Initialized telemetry portal. Connecting to RabbitMQ direct exchange...');
    }
    
    const eventSource = new EventSource('/api/stream/events');

    eventSource.onopen = () => {
      appendTerminalLine('system', '⚡ Server-Sent Events (SSE) connection established.');
    };

    eventSource.addEventListener('action', (event: any) => {
      try {
        const data = JSON.parse(event.data);
        appendTerminalLine('plan', `Telemetry: Action message captured [id=${data.actionId}, protocol=${data.protocol}]`);
        setActiveNode('execute');
      } catch (err) {
        console.error("Failed to parse action event", err);
      }
    });

    eventSource.addEventListener('status', (event: any) => {
      try {
        const data = JSON.parse(event.data);
        appendTerminalLine('execute', `Telemetry: Action status update captured [id=${data.actionId}, status=${data.status}]`);
        setActiveNode('knowledge');
      } catch (err) {
        console.error("Failed to parse status event", err);
      }
    });

    eventSource.addEventListener('graph.updates', (event: any) => {
      try {
        const data = JSON.parse(event.data);
        appendTerminalLine('monitor', `Telemetry: Graph updated [resource=${data.resourceIri}, change=${data.changeKind}]`);
        setActiveNode('analyze');
        if (data.changeKind === 'STATE_CHANGED') {
          setActiveAnomalies(prev => prev + 1);
        }
      } catch (err) {
        console.error("Failed to parse graph update event", err);
      }
    });

    eventSource.onerror = (err) => {
      console.error("SSE Connection Error", err);
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, []);

  return {
    logs,
    activeAnomalies,
    totalInterventions,
    avgLatency,
    successRate,
    appendTerminalLine,
    setActiveAnomalies,
    setTotalInterventions
  };
}
