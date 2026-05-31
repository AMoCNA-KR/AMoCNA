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
  setActiveNode: (node: string) => void;
  setIsSimulating: (status: boolean) => void;
  setActiveAnomalies: (updater: number | ((prev: number) => number)) => void;
  setTotalInterventions: (updater: number | ((prev: number) => number)) => void;
  appendTerminalLine: (tag: 'system' | 'monitor' | 'analyze' | 'execute' | 'plan', msg: string) => void;
}

export const useStore = create<AppState>((set) => ({
  activeNode: 'monitor',
  isSimulating: true,
  logs: [],
  activeAnomalies: 0,
  totalInterventions: 142,
  avgLatency: 2.48,
  successRate: 98.2,
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
}));
