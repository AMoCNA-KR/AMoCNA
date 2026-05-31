import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as Tabs from '@radix-ui/react-tabs';
import ThresholdList from './components/ThresholdList';
import ThresholdEditor from './components/ThresholdEditor';
import ThresholdStats from '../../components/ThresholdStats';
import type { Threshold } from '../../types/threshold';

const fetchThresholds = async (): Promise<Threshold[]> => {
  try {
    const res = await fetch('/api/thresholds');
    if (!res.ok) throw new Error('Failed to fetch thresholds');
    return res.json();
  } catch (err) {
    console.warn("Using mock data as API failed", err);
    return [
      { name: "critical-metric-exceeded.yml", query: "critical_metric", operator: ">", value: 100.0, anomalyState: "ContainerCPUThrottledState", resourceKind: "Pod", resourceLabel: "pod", namespaceLabel: "namespace", persistenceWindow: 0 },
      { name: "node-memory-over-90-percent.yml", query: "node_memory_Active_bytes / node_memory_MemTotal_bytes", operator: ">", value: 0.9, anomalyState: "NodeMemoryStarvedState", resourceKind: "Node", resourceLabel: "instance", namespaceLabel: null, persistenceWindow: 0 },
      { name: "filesystem-over-80-percent.yml", query: "1 - (node_filesystem_avail_bytes{mountpoint='/tmp'} / node_filesystem_size_bytes{mountpoint='/tmp'})", operator: ">", value: 0.8, anomalyState: "StorageDiskSpaceExhaustedState", resourceKind: "Node", resourceLabel: "instance", namespaceLabel: null, persistenceWindow: 0 },
      { name: "frontend-latency.yml", query: "sum(rate(request_duration_seconds_sum{name='front-end'}[1m])) by (name) / sum(rate(request_duration_seconds_count{name='front-end'}[1m]))", operator: ">", value: 0.85, anomalyState: "ResponseTimeSlaViolatedState", resourceKind: "Service", resourceLabel: "name", namespaceLabel: "kubernetes_namespace", persistenceWindow: 1 }
    ];
  }
};

const saveThreshold = async (threshold: Threshold): Promise<void> => {
  const res = await fetch(`/api/thresholds/${threshold.name}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(threshold)
  });
  if (!res.ok) {
    const errorText = await res.text();
    throw new Error(errorText || 'Failed to save threshold');
  }
};

export default function ThresholdsView() {
  const queryClient = useQueryClient();
  const { data: thresholds = [], isLoading } = useQuery({
    queryKey: ['thresholds'],
    queryFn: fetchThresholds,
  });

  const mutation = useMutation({
    mutationFn: saveThreshold,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['thresholds'] });
    },
  });

  const [editThreshold, setEditThreshold] = useState<Threshold | null>(null);

  useEffect(() => {
    if (thresholds.length > 0 && !editThreshold) {
      setEditThreshold(thresholds[0]);
    }
  }, [thresholds]);

  const handleFieldChange = (field: keyof Threshold, value: any) => {
    if (editThreshold) {
      setEditThreshold({ ...editThreshold, [field]: value });
    }
  };

  const handleSave = () => {
    if (editThreshold) {
      mutation.mutate(editThreshold);
    }
  };

  if (isLoading) return <div>Loading thresholds...</div>;

  return (
    <div className="panel">
      <Tabs.Root defaultValue="audit">
        <div className="panel-header" style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h2>⚙️ Threshold Configuration</h2>
            <p>Manage rule sets inside classpath:thresholds/</p>
          </div>
          <Tabs.List className="radix-tabs-list">
            <Tabs.Trigger value="audit" className="radix-tabs-trigger">
              📊 File Audit
            </Tabs.Trigger>
            <Tabs.Trigger value="editor" className="radix-tabs-trigger">
              ✏️ Edit Config
            </Tabs.Trigger>
          </Tabs.List>
        </div>

        <div className="config-grid">
          <ThresholdList 
            thresholds={thresholds}
            selectedThresholdName={editThreshold?.name || ''}
            loadRuleIntoEditor={(rule) => setEditThreshold(rule)}
          />

          <Tabs.Content value="editor">
            {editThreshold && (
              <ThresholdEditor 
                editThreshold={editThreshold}
                onFieldChange={handleFieldChange}
                onSave={handleSave}
                isSaving={mutation.isPending}
              />
            )}
          </Tabs.Content>
          <Tabs.Content value="audit">
            <ThresholdStats 
              ruleName={editThreshold?.name || ''}
              rule={editThreshold}
              activeNode="monitor"
              isSimulating={true}
            />
          </Tabs.Content>
        </div>
      </Tabs.Root>
    </div>
  );
}
