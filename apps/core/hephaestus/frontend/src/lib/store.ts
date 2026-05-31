import { create } from 'zustand';

interface AppState {
  activeNode: string;
  isSimulating: boolean;
  setActiveNode: (node: string) => void;
  setIsSimulating: (status: boolean) => void;
}

export const useStore = create<AppState>((set) => ({
  activeNode: 'monitor',
  isSimulating: true,
  setActiveNode: (node) => set({ activeNode: node }),
  setIsSimulating: (status) => set({ isSimulating: status }),
}));
