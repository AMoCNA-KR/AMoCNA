import * as Select from '@radix-ui/react-select';
import { Save, ChevronDown, Check } from 'lucide-react';
import type { Threshold } from '../../../types/threshold';

interface ThresholdEditorProps {
  editThreshold: Threshold;
  onFieldChange: (field: keyof Threshold, value: any) => void;
  onSave: () => void;
  isSaving: boolean;
}

export default function ThresholdEditor({
  editThreshold,
  onFieldChange,
  onSave,
  isSaving
}: ThresholdEditorProps) {
  return (
    <div className="rule-editor-pane">
      <h3 className="editor-title">✏️ Rule Configuration Editor</h3>
      <div className="editor-form">
        <div className="form-group">
          <label>Rule Filename</label>
          <input type="text" value={editThreshold.name} readOnly style={{ opacity: 0.6 }} />
        </div>

        <div className="form-group">
          <label>PromQL Query</label>
          <textarea 
            value={editThreshold.query} 
            onChange={(e) => onFieldChange('query', e.target.value)} 
            rows={2} 
          />
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Operator</label>
            <Select.Root 
              value={editThreshold.operator} 
              onValueChange={(val) => onFieldChange('operator', val)}
            >
              <Select.Trigger className="radix-select-trigger">
                <Select.Value placeholder="Select operator..." />
                <Select.Icon>
                  <ChevronDown size={14} />
                </Select.Icon>
              </Select.Trigger>

              <Select.Portal>
                <Select.Content className="radix-select-content">
                  <Select.Viewport className="radix-select-viewport">
                    {['>', '>=', '<', '<=', '=='].map((op) => (
                      <Select.Item key={op} value={op} className="radix-select-item">
                        <Select.ItemText>{op}</Select.ItemText>
                        <Select.ItemIndicator>
                          <Check size={14} />
                        </Select.ItemIndicator>
                      </Select.Item>
                    ))}
                  </Select.Viewport>
                </Select.Content>
              </Select.Portal>
            </Select.Root>
          </div>
          <div className="form-group">
            <label>Threshold Value</label>
            <input 
              type="number" 
              step="any" 
              value={editThreshold.value} 
              onChange={(e) => onFieldChange('value', parseFloat(e.target.value) || 0)} 
            />
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Persistence Window</label>
            <input 
              type="number" 
              min={0} 
              value={editThreshold.persistenceWindow} 
              onChange={(e) => onFieldChange('persistenceWindow', parseInt(e.target.value, 10) || 0)} 
            />
          </div>
          <div className="form-group">
            <label>CNEEOnt Anomaly State</label>
            <input 
              type="text" 
              value={editThreshold.anomalyState} 
              onChange={(e) => onFieldChange('anomalyState', e.target.value)} 
            />
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label>Resource Kind</label>
            <Select.Root 
              value={editThreshold.resourceKind} 
              onValueChange={(val) => onFieldChange('resourceKind', val)}
            >
              <Select.Trigger className="radix-select-trigger">
                <Select.Value placeholder="Select kind..." />
                <Select.Icon>
                  <ChevronDown size={14} />
                </Select.Icon>
              </Select.Trigger>

              <Select.Portal>
                <Select.Content className="radix-select-content">
                  <Select.Viewport className="radix-select-viewport">
                    {['Pod', 'Node', 'Service'].map((kind) => (
                      <Select.Item key={kind} value={kind} className="radix-select-item">
                        <Select.ItemText>{kind}</Select.ItemText>
                        <Select.ItemIndicator>
                          <Check size={14} />
                        </Select.ItemIndicator>
                      </Select.Item>
                    ))}
                  </Select.Viewport>
                </Select.Content>
              </Select.Portal>
            </Select.Root>
          </div>
          <div className="form-group">
            <label>Resource Label</label>
            <input 
              type="text" 
              value={editThreshold.resourceLabel} 
              onChange={(e) => onFieldChange('resourceLabel', e.target.value)} 
            />
          </div>
        </div>

        <div className="form-group">
          <label>Namespace Label (Optional)</label>
          <input 
            type="text" 
            value={editThreshold.namespaceLabel || ''} 
            onChange={(e) => onFieldChange('namespaceLabel', e.target.value || null)} 
          />
        </div>

        <button className="btn-save" onClick={onSave} disabled={isSaving}>
          <Save size={16} />
          {isSaving ? 'Saving...' : 'Save & Trigger Hot-Reload'}
        </button>
      </div>
    </div>
  );
}
