import type { Threshold } from '../../../types/threshold';

interface ThresholdListProps {
  thresholds: Threshold[];
  selectedThresholdName: string;
  loadRuleIntoEditor: (rule: Threshold) => void;
}

export default function ThresholdList({ thresholds, selectedThresholdName, loadRuleIntoEditor }: ThresholdListProps) {
  return (
    <div className="rules-sidebar">
      {thresholds.map((rule, idx) => (
        <div 
          key={idx} 
          className={`rule-card ${selectedThresholdName === rule.name ? 'selected' : ''}`}
          onClick={() => loadRuleIntoEditor(rule)}
        >
          <div className="rule-meta">
            <span className="rule-name">{rule.name}</span>
            <span className="rule-query" title={rule.query}>{rule.query}</span>
          </div>
          <span className="rule-badge">{rule.operator} {rule.value}</span>
        </div>
      ))}
    </div>
  );
}
