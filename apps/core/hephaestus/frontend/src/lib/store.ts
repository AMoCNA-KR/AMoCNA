import { create } from 'zustand';
import type { LogEntry } from '../types/telemetry';

interface AppState {
  activeNode: string;
  isSimulating: boolean;
  logs: LogEntry[];
  activeAnomalies: number;
  totalInterventions: number;
  avgLatency: number;
  successRate: number;
  servicesHealth: Record<string, 'UP' | 'DOWN' | 'CHECKING'>;
  setActiveNode: (node: string) => void;
  setIsSimulating: (status: boolean) => void;
  setActiveAnomalies: (updater: number | ((prev: number) => number)) => void;
  setTotalInterventions: (updater: number | ((prev: number) => number)) => void;
  appendTerminalLine: (tag: 'system' | 'monitor' | 'analyze' | 'execute' | 'plan', msg: string) => void;
  updateServiceHealth: (service: string, status: 'UP' | 'DOWN') => void;
  setRealMetrics: (latency: number, successRate: number) => void;
}

export const useStore = create<AppState>((set) => ({
  activeNode: 'monitor',
  isSimulating: true,
  logs: [],
  activeAnomalies: 0,
  totalInterventions: 142,
  avgLatency: 0.35, // Updated to a realistic base value
  successRate: 100.0,
  servicesHealth: {
    themis: 'CHECKING',
    palamedes: 'CHECKING',
    metis: 'CHECKING',
    'metrics-adapter': 'CHECKING',
    graphdb: 'CHECKING',
    rabbitmq: 'CHECKING',
  },
  setActiveNode: (node) => set({ activeNode: node }),
  setIsSimulating: (status) => set({ isSimulating: status }),
  setActiveAnomalies: (updater) => set((state) => ({
    activeAnomalies: typeof updater === 'function' ? updater(state.activeAnomalies) : updater
  })),
  setTotalInterventions: (updater) => set((state) => ({
    totalInterventions: typeof updater === 'function' ? updater(state.totalInterventions) : updater
  })),
  appendTerminalLine: (tag, msg) => set((state) => {
    const time = new Date().toTimeString().split(' ')[0];
    return {
      logs: [...state.logs, { time, tag, msg }]
    };
  }),
  updateServiceHealth: (service, status) => set((state) => ({
    servicesHealth: {
      ...state.servicesHealth,
      [service]: status,
    }
  })),
  setRealMetrics: (latency, successRate) => set({
    avgLatency: latency,
    successRate: successRate,
  }),
}));
