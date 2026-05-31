import KpiRow from '../../components/KpiRow';
import MapeLoop from '../../components/MapeLoop';
import EventTerminal from '../../components/EventTerminal';
import { useTelemetry } from '../../hooks/useTelemetry';
import styles from './DashboardView.module.css';

export default function DashboardView() {
  const {
    logs,
    activeAnomalies,
    totalInterventions,
    avgLatency,
    successRate
  } = useTelemetry();

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
          <MapeLoop />
          <EventTerminal logs={logs} />
        </div>
      </div>
    </>
  );
}
