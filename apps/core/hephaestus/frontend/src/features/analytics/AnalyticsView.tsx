import { useState, useEffect } from 'react';
import TelemetryChart from '../../components/TelemetryChart';
import EventTerminal from '../../components/EventTerminal';
import { useTelemetry } from '../../hooks/useTelemetry';
import { useStore } from '../../lib/store';
import styles from './AnalyticsView.module.css';

export default function AnalyticsView() {
  const { activeNode, isSimulating } = useStore();
  const { logs } = useTelemetry();
  const [chartData, setChartData] = useState<{ time: string; latency: number; cpu: number }[]>([]);

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

  return (
    <div className={styles.analyticsLayout}>
      <TelemetryChart data={chartData} />
      <EventTerminal logs={logs} />
    </div>
  );
}
