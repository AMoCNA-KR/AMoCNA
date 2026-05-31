import styles from './KpiRow.module.css';

interface KpiRowProps {
  avgLatency: number;
  successRate: number;
  activeAnomalies: number;
  totalInterventions: number;
}

export default function KpiRow({ avgLatency, successRate, activeAnomalies, totalInterventions }: KpiRowProps) {
  return (
    <div className={styles.kpiRow}>
      <div className={styles.kpiCard}>
        <span className={styles.kpiTitle}>Avg Loop Latency</span>
        <span className={styles.kpiValue}>{avgLatency}s</span>
        <span className={`${styles.kpiTrend} ${styles.trendUp}`}>▼ 12% faster remediation</span>
      </div>
      <div className={styles.kpiCard}>
        <span className={styles.kpiTitle}>Success Rate</span>
        <span className={styles.kpiValue}>{successRate}%</span>
        <span className={`${styles.kpiTrend} ${styles.trendUp}`}>▲ 1.4% improvement</span>
      </div>
      <div className={styles.kpiCard} style={{ borderColor: activeAnomalies > 0 ? 'var(--c-monitor)' : '' }}>
        <span className={styles.kpiTitle}>Active Anomalies</span>
        <span className={styles.kpiValue} style={{ color: activeAnomalies > 0 ? 'var(--c-monitor)' : '' }}>{activeAnomalies}</span>
        <span className={styles.kpiTrend}>{activeAnomalies > 0 ? 'Remediation active' : 'System healthy'}</span>
      </div>
      <div className={styles.kpiCard}>
        <span className={styles.kpiTitle}>Autonomous Actions</span>
        <span className={styles.kpiValue}>{totalInterventions}</span>
        <span className={`${styles.kpiTrend} ${styles.trendUp}`}>Self-healing active</span>
      </div>
    </div>
  );
}
