# Dashboard Feature Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the Dashboard feature from `App.tsx` to a modular structure in `src/features/dashboard`, using a shared store for state.

**Architecture:**
- Create `DashboardView` as the main container for the dashboard.
- Refactor `MapeLoop` to use `useStore` for `activeNode` and `isSimulating`.
- Move `triggerMockViolation` logic to `DashboardView`.
- Update `useTelemetry` to use the shared store for setting `activeNode`.
- Connect `DashboardView` to the root route (`/`).

**Tech Stack:** React, TypeScript, Zustand, TanStack Router.

---

### Task 1: Update useTelemetry to use Store

**Files:**
- Modify: `apps/core/hephaestus/frontend/src/hooks/useTelemetry.ts`

- [ ] **Step 1: Refactor useTelemetry to use useStore**

```typescript
import { useState, useEffect } from 'react';
import type { LogEntry } from '../types/telemetry';
import { useStore } from '../lib/store';

export function useTelemetry() {
  const setActiveNode = useStore((state) => state.setActiveNode);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  // ... rest of state
```

- [ ] **Step 2: Commit**

```bash
git add apps/core/hephaestus/frontend/src/hooks/useTelemetry.ts
git commit -m "refactor: update useTelemetry to use central store"
```

### Task 2: Refactor MapeLoop to use Store

**Files:**
- Modify: `apps/core/hephaestus/frontend/src/components/MapeLoop.tsx`

- [ ] **Step 1: Remove props and use useStore**

Update `MapeLoop` to get `activeNode`, `isSimulating`, and `setIsSimulating` from `useStore`. Keep `triggerMockViolation` as a prop for now or decide where it should live.

- [ ] **Step 2: Commit**

```bash
git add apps/core/hephaestus/frontend/src/components/MapeLoop.tsx
git commit -m "refactor: MapeLoop uses central store"
```

### Task 3: Create DashboardView

**Files:**
- Create: `apps/core/hephaestus/frontend/src/features/dashboard/DashboardView.tsx`

- [ ] **Step 1: Implement DashboardView**

Move the Dashboard-related layout and logic from `App.tsx` to `DashboardView.tsx`. This includes `chartData` state, `useTelemetry`, and `triggerMockViolation`.

- [ ] **Step 2: Commit**

```bash
git add apps/core/hephaestus/frontend/src/features/dashboard/DashboardView.tsx
git commit -m "feat: create DashboardView"
```

### Task 4: Connect to Index Route

**Files:**
- Modify: `apps/core/hephaestus/frontend/src/routes/index.tsx`

- [ ] **Step 1: Render DashboardView**

Replace placeholder content with `DashboardView`.

- [ ] **Step 2: Commit**

```bash
git add apps/core/hephaestus/frontend/src/routes/index.tsx
git commit -m "feat: connect DashboardView to index route"
```

### Task 5: Clean up App.tsx (Optional but good)

**Files:**
- Modify: `apps/core/hephaestus/frontend/src/App.tsx` (if needed)

Since we are moving to a router, `App.tsx` might not be needed anymore or should be significantly reduced.
Actually, the instructions don't say to delete `App.tsx` yet, but it's good to keep in mind.
