import { useState, useEffect } from 'react';
import { FileText, ShieldAlert, BarChart3, Layers, Terminal } from 'lucide-react';
import type { Threshold } from '../types/threshold';
import styles from './ThresholdStats.module.css';
import editorStyles from '../features/thresholds/components/ThresholdEditor.module.css';
import formStyles from './Forms.module.css';

interface ThresholdStatsProps {
  ruleName: string;
  rule: Threshold | null;
  activeNode: string;
  isSimulating: boolean;
}

export default function ThresholdStats({ ruleName, rule, activeNode, isSimulating }: ThresholdStatsProps) {
  const [scanCount, setScanCount] = useState<number>(1284);
  const [triggerCount, setTriggerCount] = useState<number>(0);
  const [lastBreach, setLastBreach] = useState<string>('Never');
  
  // Set file specific breach stats
  useEffect(() => {
    if (!ruleName) return;
    let hash = 0;
    for (let i = 0; i < ruleName.length; i++) {
      hash = ruleName.charCodeAt(i) + ((hash << 5) - hash);
    }
    const seedScans = 1000 + Math.abs(hash % 1000);
    const seedTriggers = Math.abs(hash % 12);
    setScanCount(seedScans);
    setTriggerCount(seedTriggers);
    setLastBreach(seedTriggers > 0 ? `${Math.abs(hash % 45) + 3}m ago` : 'Never');
  }, [ruleName]);

  // Live scan incrementer
  useEffect(() => {
    const interval = setInterval(() => {
      setScanCount(prev => prev + 1);
    }, 4000);
    return () => clearInterval(interval);
  }, []);

  if (!rule) {
    return (
      <div className={editorStyles.ruleEditorPane} style={{ justifyContent: 'center', alignItems: 'center', minHeight: '300px' }}>
        <p style={{ color: 'var(--text-muted)' }}>Select a threshold file to inspect stats</p>
      </div>
    );
  }

  // Is this specific file currently in breach?
  const isCurrentlyBreached = !isSimulating && ruleName === 'frontend-latency.yml' && activeNode !== 'knowledge';
  
  // Per-file custom historical audit logs
  const getLogsForFile = () => {
    const nowStr = new Date().toTimeString().split(' ')[0];
    const pastStr1 = new Date(Date.now() - 30000).toTimeString().split(' ')[0];
    const pastStr2 = new Date(Date.now() - 60000).toTimeString().split(' ')[0];

    if (isCurrentlyBreached) {
      return [
        { time: pastStr2, type: 'ok', text: `Scan passed: latency 0.392s <= SLA ${rule.value}s` },
        { time: pastStr1, type: 'ok', text: `Scan passed: latency 0.407s <= SLA ${rule.value}s` },
        { time: nowStr, type: 'breach', text: `🔴 CRITICAL SLA BREACH: latency 0.974s > ${rule.value}s!` },
        { time: nowStr, type: 'action', text: `Dispatched GraphUpdateMessage: ${rule.anomalyState}` },
        { time: nowStr, type: 'action', text: `Autonomic Loop active [State: ${activeNode.toUpperCase()}]` }
      ];
    }

    if (ruleName === 'frontend-latency.yml') {
      return [
        { time: pastStr2, type: 'ok', text: `Scan passed: latency 0.381s <= SLA ${rule.value}s` },
        { time: pastStr1, type: 'ok', text: `Scan passed: latency 0.412s <= SLA ${rule.value}s` },
        { time: nowStr, type: 'ok', text: `Scan passed: latency 0.395s <= SLA ${rule.value}s` }
      ];
    } else if (ruleName === 'node-memory-over-90-percent.yml') {
      return [
        { time: pastStr2, type: 'ok', text: `RAM usage 68.2% <= threshold ${rule.value * 100}%` },
        { time: pastStr1, type: 'ok', text: `RAM usage 68.5% <= threshold ${rule.value * 100}%` },
        { time: nowStr, type: 'ok', text: `RAM usage 67.9% <= threshold ${rule.value * 100}%` }
      ];
    } else if (ruleName === 'filesystem-over-80-percent.yml') {
      return [
        { time: pastStr2, type: 'ok', text: `Filesystem tmp usage 14.2% <= threshold ${rule.value * 100}%` },
        { time: pastStr1, type: 'ok', text: `Filesystem tmp usage 14.2% <= threshold ${rule.value * 100}%` },
        { time: nowStr, type: 'ok', text: `Filesystem tmp usage 14.3% <= threshold ${rule.value * 100}%` }
      ];
    } else {
      return [
        { time: pastStr2, type: 'ok', text: `Metric scan matches query: OK` },
        { time: pastStr1, type: 'ok', text: `Metric scan matches query: OK` },
        { time: nowStr, type: 'ok', text: `Metric value meets operator criteria: Passed` }
      ];
    }
  };

  const fileLogs = getLogsForFile();

  const getTickerMsgClass = (type: string) => {
    if (type === 'breach') return styles.tickerMsgBreach;
    if (type === 'action') return styles.tickerMsgAction;
    return styles.tickerMsgOk;
  };

  return (
    <div className={editorStyles.ruleEditorPane} style={{ gap: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid rgba(255,255,255,0.05)', paddingBottom: '12px' }}>
        <h3 className={editorStyles.editorTitle} style={{ display: 'flex', alignItems: 'center', gap: '8px', border: 'none', padding: 0 }}>
          <FileText size={16} style={{ color: 'var(--c-monitor)' }} /> 
          File Audit: {ruleName}
        </h3>
        <span className={`${styles.breachStatus} ${isCurrentlyBreached ? styles.active : styles.healthy}`}>
          {isCurrentlyBreached ? '🔴 BREACH STATE' : '💚 ACTIVE SCANNING'}
        </span>
      </div>

      {/* Stats Block */}
      <div className={styles.statsGrid}>
        <div className={styles.statCard}>
          <BarChart3 size={20} style={{ color: 'var(--c-monitor)' }} />
          <div>
            <div className={styles.statLabel}>Total Evaluated Scans</div>
            <div className={styles.statValue}>{scanCount}</div>
          </div>
        </div>
        <div className={styles.statCard}>
          <ShieldAlert size={20} style={{ color: isCurrentlyBreached ? 'var(--c-execute)' : 'var(--c-plan)' }} />
          <div>
            <div className={styles.statLabel}>Historical Anomalies</div>
            <div className={styles.statValue}>{triggerCount + (isCurrentlyBreached ? 1 : 0)}</div>
          </div>
        </div>
      </div>

      {/* Target Resource Metadata */}
      <div className={styles.resourceMetadata}>
        <div className={styles.metadataRow}>
          <span className={styles.metadataLabel}>CNEEOnt Node Class:</span>
          <span className={styles.metadataValue}>
            <Layers size={12} style={{ color: 'var(--c-analyze)' }} /> {rule.resourceKind}
          </span>
        </div>
        <div className={styles.metadataRow}>
          <span className={styles.metadataLabel}>Target Selector Name:</span>
          <span className={styles.metadataValueMono}>{rule.resourceLabel}="{ruleName.includes('latency') ? 'front-end' : 'cluster-node'}"</span>
        </div>
        <div className={styles.metadataRow}>
          <span className={styles.metadataLabel}>Trigger Mode:</span>
          <span className={styles.metadataValue}>Persistence ({rule.persistenceWindow}m window)</span>
        </div>
        <div className={styles.metadataRow}>
          <span className={styles.metadataLabel}>Last Violation Record:</span>
          <span className={styles.metadataValue} style={{ color: isCurrentlyBreached ? 'var(--c-execute)' : '' }}>
            {isCurrentlyBreached ? 'Active ⚡' : lastBreach}
          </span>
        </div>
      </div>

      {/* Mini Terminal specific for this file */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        <label className={formStyles.label} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Terminal size={12} /> File Scans Ticker
        </label>
        <div className={styles.tickerBox}>
          {fileLogs.map((log, idx) => (
            <div key={idx} className={styles.tickerLine}>
              <span className={styles.tickerTime}>{log.time}</span>
              <span className={getTickerMsgClass(log.type)}>{log.text}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
