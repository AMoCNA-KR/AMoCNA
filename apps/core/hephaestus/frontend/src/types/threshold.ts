export interface Threshold {
  name: string;
  query: string;
  operator: string;
  value: number;
  anomalyState: string;
  resourceKind: 'Pod' | 'Node' | 'Service';
  resourceLabel: string;
  namespaceLabel: string | null;
  persistenceWindow: number;
}
