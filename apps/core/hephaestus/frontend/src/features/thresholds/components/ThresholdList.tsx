import type { Threshold } from '../../../types/threshold';
import styles from './ThresholdList.module.css';

interface ThresholdListProps {
  thresholds: Threshold[];
  selectedThresholdName: string;
  loadRuleIntoEditor: (rule: Threshold) => void;
}

export default function ThresholdList({ thresholds, selectedThresholdName, loadRuleIntoEditor }: ThresholdListProps) {
  return (
    <div className={styles.rulesSidebar}>
      {thresholds.map((rule, idx) => (
        <div 
          key={idx} 
          className={`${styles.ruleCard} ${selectedThresholdName === rule.name ? styles.selected : ''}`}
          onClick={() => loadRuleIntoEditor(rule)}
        >
          <div className={styles.ruleMeta}>
            <span className={styles.ruleName}>{rule.name}</span>
            <span className={styles.ruleQuery} title={rule.query}>{rule.query}</span>
          </div>
          <span className={styles.ruleBadge}>{rule.operator} {rule.value}</span>
        </div>
      ))}
    </div>
  );
}
