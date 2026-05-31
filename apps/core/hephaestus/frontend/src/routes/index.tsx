import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/')({
  component: () => (
    <div style={{ padding: '2rem' }}>
      <h1>Dashboard</h1>
      <p>Dashboard content will be moved here from App.tsx in subsequent tasks.</p>
    </div>
  ),
});
