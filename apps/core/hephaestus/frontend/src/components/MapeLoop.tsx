import { Cpu, Compass, Layers, Server, Database, Play, Pause } from 'lucide-react';
import { useStore } from '../lib/store';

interface MapeLoopProps {
  triggerMockViolation: () => void;
}

export default function MapeLoop({ triggerMockViolation }: MapeLoopProps) {
  const { activeNode, isSimulating, setIsSimulating } = useStore();
  return (
    <div className="panel">
      <div className="panel-header">
        <div>
          <h2>🔄 Autonomic Loop Map</h2>
          <p>Observe state-changes and telemetry propagation</p>
        </div>
        <div className="control-buttons">
          <button 
            className={`btn-control ${isSimulating ? 'active' : ''}`}
            onClick={() => setIsSimulating(!isSimulating)}
          >
            {isSimulating ? <Pause size={12} /> : <Play size={12} />}
            {isSimulating ? 'Pause Sim' : 'Resume Sim'}
          </button>
          <button className="btn-control trigger-btn" onClick={triggerMockViolation}>
            Mock CPU Violation
          </button>
        </div>
      </div>

      <div className="loop-view-box">
        <svg className="loop-svg">
          <path d="M 400 70 Q 550 100 550 170" className={`flow-line ${activeNode === 'monitor' ? 'active' : ''}`} style={{ stroke: 'var(--c-monitor)' }} />
          <path d="M 550 170 Q 550 250 480 280" className={`flow-line ${activeNode === 'analyze' ? 'active' : ''}`} style={{ stroke: 'var(--c-analyze)' }} />
          <path d="M 480 280 Q 400 300 320 280" className={`flow-line ${activeNode === 'plan' ? 'active' : ''}`} style={{ stroke: 'var(--c-plan)' }} />
          <path d="M 320 280 Q 250 250 250 170" className={`flow-line ${activeNode === 'execute' ? 'active' : ''}`} style={{ stroke: 'var(--c-execute)' }} />
          <path d="M 250 170 Q 250 100 400 70" className={`flow-line ${activeNode === 'knowledge' ? 'active' : ''}`} style={{ stroke: 'var(--c-knowledge)' }} />
        </svg>

        <div className={`loop-node node-monitor ${activeNode === 'monitor' ? 'active' : ''}`} style={{ color: 'var(--c-monitor)' }}>
          <Cpu size={18} />
          M
          <span>Monitor</span>
        </div>
        <div className={`loop-node node-analyze ${activeNode === 'analyze' ? 'active' : ''}`} style={{ color: 'var(--c-analyze)' }}>
          <Compass size={18} />
          A
          <span>Reason</span>
        </div>
        <div className={`loop-node node-plan ${activeNode === 'plan' ? 'active' : ''}`} style={{ color: 'var(--c-plan)' }}>
          <Layers size={18} />
          P
          <span>Plan</span>
        </div>
        <div className={`loop-node node-execute ${activeNode === 'execute' ? 'active' : ''}`} style={{ color: 'var(--c-execute)' }}>
          <Server size={18} />
          E
          <span>Execute</span>
        </div>
        <div className={`loop-node node-knowledge ${activeNode === 'knowledge' ? 'active' : ''}`} style={{ color: 'var(--c-knowledge)' }}>
          <Database size={18} />
          K
          <span>Knowledge</span>
        </div>
      </div>
    </div>
  );
}
