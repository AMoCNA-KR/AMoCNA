# Hephaestus UI Modular Refactor Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the Hephaestus frontend into a modular, router-based application using TanStack Router, Radix UI, and Zustand.

**Architecture:** Features-based folder structure (`src/features/*`) with dedicated routes for Dashboard, Thresholds, and Analytics. Logic is extracted from components into Zustand stores and TanStack Query hooks.

**Tech Stack:** React 19, TypeScript 6, TanStack Router, TanStack Query, Radix UI, Zustand, Vanilla CSS.

---

### Task 1: Environment & Dependency Setup

**Files:**
- Modify: `apps/core/hephaestus/frontend/package.json`
- Create: `apps/core/hephaestus/frontend/src/lib/query-client.ts`
- Create: `apps/core/hephaestus/frontend/src/lib/store.ts`

- [x] **Step 1: Install SOTA dependencies**
- [x] **Step 2: Initialize Query Client**
- [x] **Step 3: Initialize Zustand Store**
- [x] **Step 4: Commit**

---

### Task 2: Routing Infrastructure

**Files:**
- Create: `apps/core/hephaestus/frontend/src/routes/__root.tsx`
- Create: `apps/core/hephaestus/frontend/src/routes/index.tsx`
- Create: `apps/core/hephaestus/frontend/src/routes/thresholds.tsx`
- Modify: `apps/core/hephaestus/frontend/src/main.tsx`

- [x] **Step 1: Create Root Route (Layout)**
- [x] **Step 2: Create Index and Thresholds Routes**
- [x] **Step 3: Wire up Router in main.tsx**
- [x] **Step 4: Commit**

---

### Task 3: Feature Migration - Dashboard

**Files:**
- Create: `apps/core/hephaestus/frontend/src/features/dashboard/DashboardView.tsx`
- Modify: `apps/core/hephaestus/frontend/src/routes/index.tsx`

- [x] **Step 1: Create Dashboard View**
- [x] **Step 2: Connect to Index Route**
- [x] **Step 3: Commit**

---

### Task 4: Feature Migration - Thresholds

**Files:**
- Create: `apps/core/hephaestus/frontend/src/features/thresholds/ThresholdsView.tsx`
- Create: `apps/core/hephaestus/frontend/src/features/thresholds/components/ThresholdEditor.tsx`

- [x] **Step 1: Implement Radix-based Threshold Editor**
- [x] **Step 2: Implement Threshold List with TanStack Query**
- [x] **Step 3: Commit**

---

### Task 5: Sidebar Layout Migration

**Files:**
- Create: `apps/core/hephaestus/frontend/src/components/Sidebar.tsx`
- Create: `apps/core/hephaestus/frontend/src/components/Sidebar.module.css`
- Modify: `apps/core/hephaestus/frontend/src/routes/__root.tsx`

- [x] **Step 1: Implement Sidebar component**
Base: `src/components/Sidebar.tsx` using Radix primitives or pure React with TanStack Link.

- [x] **Step 2: Update Root Route to use Sidebar**
Modified `__root.tsx` to replace top nav with a side-by-side layout (Sidebar + Content).

- [x] **Step 3: Commit**

```bash
git add src/components/Sidebar* src/routes/__root.tsx
git commit -m "feat: migrate to sidebar-based layout"
```

---

### Task 6: Feature Migration - Analytics

**Files:**
- Create: `apps/core/hephaestus/frontend/src/features/analytics/AnalyticsView.tsx`
- Create: `apps/core/hephaestus/frontend/src/routes/analytics.tsx`
- Modify: `apps/core/hephaestus/frontend/src/features/dashboard/DashboardView.tsx`

- [x] **Step 1: Create Analytics View**
Moved `TelemetryChart` and `EventTerminal` from `DashboardView.tsx` into `AnalyticsView.tsx`.

- [x] **Step 2: Implement Analytics Route**
Created `src/routes/analytics.tsx` and exported the route.

- [x] **Step 3: Commit**

```bash
git add src/features/analytics/ src/routes/analytics.tsx src/features/dashboard/DashboardView.tsx
git commit -m "feat: implement dedicated analytics view"
```

---

### Task 7: Cleanup & Final Refinement

- [x] **Step 1: Delete App.tsx**
- [x] **Step 2: Consolidate legacy index.css**
- [x] **Step 3: Final verification of simulation logic**
- [x] **Step 4: Commit**

