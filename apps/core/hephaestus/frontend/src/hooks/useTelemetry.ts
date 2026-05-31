import { useState, useEffect } from 'react';
import type { LogEntry } from '../types/telemetry';
import { useStore } from '../lib/store';

export function useTelemetry() {
  const setActiveNode = useStore((state) => state.setActiveNode);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [activeAnomalies, setActiveAnomalies] = useState<number>(0);
  const [totalInterventions, setTotalInterventions] = useState<number>(142);
  const [avgLatency] = useState<number>(2.48);
  const [successRate] = useState<number>(98.2);

  const appendTerminalLine = (tag: 'system' | 'monitor' | 'analyze' | 'execute' | 'plan', msg: string) => {
    const time = new Date().toTimeString().split(' ')[0];
    setLogs(prev => [...prev, { time, tag, msg }]);
  };

  // Setup EventSource subscription
  useEffect(() => {
    appendTerminalLine('system', 'Initialized telemetry portal. Connecting to RabbitMQ direct exchange...');
    
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
