import { useMemo } from 'react';
import ReactFlow, { 
  Background, 
  Controls, 
  Handle, 
  Position, 
  MarkerType,
  type Node,
  type Edge
} from 'reactflow';
import 'reactflow/dist/style.css';
import { 
  Cpu, 
  Compass, 
  Layers, 
  Server, 
  Database, 
  MessageSquare
} from 'lucide-react';
import { useStore } from '../lib/store';
import styles from './MapeLoop.module.css';
import panelStyles from './Panel.module.css';

// 1. Custom Node Component
interface CustomNodeProps {
  data: {
    name: string;
    role: string;
    stage: string;
    health: 'UP' | 'DOWN' | 'CHECKING';
    isActive: boolean;
    color: string;
    icon: React.ReactNode;
  };
}

function MapeNode({ data }: CustomNodeProps) {
  return (
    <div 
      className={`${styles.customNode} ${data.isActive ? styles.nodeActive : ''}`} 
      style={{ '--node-color': data.color } as any}
    >
      {/* Target Handles */}
      <Handle type="target" position={Position.Top} id="t-top" className={styles.handle} />
      <Handle type="target" position={Position.Bottom} id="t-bottom" className={styles.handle} />
      <Handle type="target" position={Position.Left} id="t-left" className={styles.handle} />
      <Handle type="target" position={Position.Right} id="t-right" className={styles.handle} />

      <div className={styles.nodeHeader}>
        <span className={styles.nodeStage}>{data.stage}</span>
        <span className={`${styles.healthBadge} ${
          data.health === 'UP' ? styles.healthUp : 
          data.health === 'DOWN' ? styles.healthDown : styles.healthChecking
        }`}>
          {data.health}
        </span>
      </div>

      <div className={styles.nodeBody}>
        <div className={styles.nodeIcon} style={{ color: data.color }}>
          {data.icon}
        </div>
        <div className={styles.nodeDetails}>
          <div className={styles.nodeName}>{data.name}</div>
          <div className={styles.nodeRole}>{data.role}</div>
        </div>
      </div>

      {/* Source Handles */}
      <Handle type="source" position={Position.Top} id="s-top" className={styles.handle} />
      <Handle type="source" position={Position.Bottom} id="s-bottom" className={styles.handle} />
      <Handle type="source" position={Position.Left} id="s-left" className={styles.handle} />
      <Handle type="source" position={Position.Right} id="s-right" className={styles.handle} />
    </div>
  );
}

// Custom Node types registry for ReactFlow
const nodeTypes = {
  mapeNode: MapeNode,
};

export default function MapeLoop() {
  const { activeNode, activeAnomalies, servicesHealth } = useStore();

  // Define static node positions and stages
  const rawNodes = useMemo(() => [
    {
      id: 'metrics-adapter',
      type: 'mapeNode',
      position: { x: 300, y: 20 },
      data: {
        name: 'metrics-adapter',
        role: 'Prometheus Collector',
        stage: 'MONITOR',
        color: 'var(--c-monitor)',
        icon: <Cpu size={20} />,
      },
    },
    {
      id: 'palamedes',
      type: 'mapeNode',
      position: { x: 550, y: 120 },
      data: {
        name: 'palamedes',
        role: 'Semantic Reasoner',
        stage: 'ANALYZE',
        color: 'var(--c-analyze)',
        icon: <Compass size={20} />,
      },
    },
    {
      id: 'metis',
      type: 'mapeNode',
      position: { x: 500, y: 320 },
      data: {
        name: 'metis',
        role: 'Knowledge Manager',
        stage: 'PLAN',
        color: 'var(--c-plan)',
        icon: <Layers size={20} />,
      },
    },
    {
      id: 'themis',
      type: 'mapeNode',
      position: { x: 100, y: 320 },
      data: {
        name: 'themis',
        role: 'Autonomic Executor',
        stage: 'EXECUTE',
        color: 'var(--c-execute)',
        icon: <Server size={20} />,
      },
    },
    {
      id: 'graphdb',
      type: 'mapeNode',
      position: { x: 50, y: 120 },
      data: {
        name: 'graphdb',
        role: 'RDF Ontology Store',
        stage: 'KNOWLEDGE',
        color: 'var(--c-knowledge)',
        icon: <Database size={20} />,
      },
    },
    {
      id: 'rabbitmq',
      type: 'mapeNode',
      position: { x: 300, y: 170 },
      data: {
        name: 'rabbitmq',
        role: 'Event Message Broker',
        stage: 'AMOCNA BUS',
        color: '#ff7a00',
        icon: <MessageSquare size={20} />,
      },
    },
  ], []);

  // Sync node dynamic properties (health & active state)
  const nodes: Node[] = useMemo(() => {
    return rawNodes.map((n) => {
      const isNodeActive = 
        (n.id === 'metrics-adapter' && activeNode === 'monitor') ||
        (n.id === 'palamedes' && activeNode === 'analyze') ||
        (n.id === 'metis' && activeNode === 'plan') ||
        (n.id === 'themis' && activeNode === 'execute') ||
        (n.id === 'graphdb' && activeNode === 'knowledge') ||
        (n.id === 'rabbitmq' && activeAnomalies > 0); // Pulsate broker when real anomalies are active

      return {
        ...n,
        data: {
          ...n.data,
          health: servicesHealth[n.id] || 'CHECKING',
          isActive: isNodeActive,
        },
      };
    });
  }, [rawNodes, activeNode, servicesHealth, activeAnomalies]);

  // Dynamically define connections (edges) with active path animations
  const edges: Edge[] = useMemo(() => {
    const defaultEdgeStyle = { stroke: 'rgba(255, 255, 255, 0.08)', strokeWidth: 1.5 };
    const activeEdgeStyle = (color: string) => ({
      stroke: color,
      strokeWidth: 3,
      filter: `drop-shadow(0 0 8px ${color})`,
    });

    return [
      // Primary Autonomic Circular Loop Edges
      {
        id: 'monitor-analyze',
        source: 'metrics-adapter',
        target: 'palamedes',
        sourceHandle: 's-right',
        targetHandle: 't-top',
        animated: activeNode === 'monitor',
        style: activeNode === 'monitor' ? activeEdgeStyle('var(--c-monitor)') : defaultEdgeStyle,
        markerEnd: { type: MarkerType.ArrowClosed, color: activeNode === 'monitor' ? 'var(--c-monitor)' : 'rgba(255,255,255,0.2)' },
      },
      {
        id: 'analyze-plan',
        source: 'palamedes',
        target: 'metis',
        sourceHandle: 's-bottom',
        targetHandle: 't-top',
        animated: activeNode === 'analyze',
        style: activeNode === 'analyze' ? activeEdgeStyle('var(--c-analyze)') : defaultEdgeStyle,
        markerEnd: { type: MarkerType.ArrowClosed, color: activeNode === 'analyze' ? 'var(--c-analyze)' : 'rgba(255,255,255,0.2)' },
      },
      {
        id: 'plan-execute',
        source: 'metis',
        target: 'themis',
        sourceHandle: 's-left',
        targetHandle: 't-right',
        animated: activeNode === 'plan',
        style: activeNode === 'plan' ? activeEdgeStyle('var(--c-plan)') : defaultEdgeStyle,
        markerEnd: { type: MarkerType.ArrowClosed, color: activeNode === 'plan' ? 'var(--c-plan)' : 'rgba(255,255,255,0.2)' },
      },
      {
        id: 'execute-knowledge',
        source: 'themis',
        target: 'graphdb',
        sourceHandle: 's-top',
        targetHandle: 't-bottom',
        animated: activeNode === 'execute',
        style: activeNode === 'execute' ? activeEdgeStyle('var(--c-execute)') : defaultEdgeStyle,
        markerEnd: { type: MarkerType.ArrowClosed, color: activeNode === 'execute' ? 'var(--c-execute)' : 'rgba(255,255,255,0.2)' },
      },
      {
        id: 'knowledge-monitor',
        source: 'graphdb',
        target: 'metrics-adapter',
        sourceHandle: 's-top',
        targetHandle: 't-left',
        animated: activeNode === 'knowledge',
        style: activeNode === 'knowledge' ? activeEdgeStyle('var(--c-knowledge)') : defaultEdgeStyle,
        markerEnd: { type: MarkerType.ArrowClosed, color: activeNode === 'knowledge' ? 'var(--c-knowledge)' : 'rgba(255,255,255,0.2)' },
      },

      // Messaging Bus Broker Connections (RabbitMQ integration)
      {
        id: 'metrics-rabbitmq',
        source: 'metrics-adapter',
        target: 'rabbitmq',
        sourceHandle: 's-bottom',
        targetHandle: 't-top',
        style: { stroke: 'rgba(255, 122, 0, 0.15)', strokeDasharray: '4 4' },
      },
      {
        id: 'rabbitmq-palamedes',
        source: 'rabbitmq',
        target: 'palamedes',
        sourceHandle: 's-right',
        targetHandle: 't-left',
        style: { stroke: 'rgba(255, 122, 0, 0.15)', strokeDasharray: '4 4' },
      },
      {
        id: 'rabbitmq-themis',
        source: 'rabbitmq',
        target: 'themis',
        sourceHandle: 's-left',
        targetHandle: 't-top',
        style: { stroke: 'rgba(255, 122, 0, 0.15)', strokeDasharray: '4 4' },
      },
    ];
  }, [activeNode]);

  return (
    <div className={panelStyles.panel}>
      <div className={panelStyles.panelHeader}>
        <div>
          <h2>🔄 Dynamic MAPE-K Autonomic Loop</h2>
          <p>Real-time system mapping with automated microservice pod status probe detection</p>
        </div>
      </div>

      <div className={styles.flowWrapper}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.15 }}
          nodesDraggable={false}
          nodesConnectable={false}
          elementsSelectable={false}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="rgba(255,255,255,0.03)" gap={20} size={1} />
          <Controls className={styles.flowControls} showInteractive={false} />
        </ReactFlow>
      </div>

      <div className={styles.legendRow}>
        <div className={styles.legendItem}>
          <div className={styles.legendColor} style={{ backgroundColor: 'var(--c-monitor)' }}></div>
          <span>Monitor</span>
        </div>
        <div className={styles.legendItem}>
          <div className={styles.legendColor} style={{ backgroundColor: 'var(--c-analyze)' }}></div>
          <span>Analyze</span>
        </div>
        <div className={styles.legendItem}>
          <div className={styles.legendColor} style={{ backgroundColor: 'var(--c-plan)' }}></div>
          <span>Plan</span>
        </div>
        <div className={styles.legendItem}>
          <div className={styles.legendColor} style={{ backgroundColor: 'var(--c-execute)' }}></div>
          <span>Execute</span>
        </div>
        <div className={styles.legendItem}>
          <div className={styles.legendColor} style={{ backgroundColor: 'var(--c-knowledge)' }}></div>
          <span>Knowledge</span>
        </div>
      </div>
    </div>
  );
}
