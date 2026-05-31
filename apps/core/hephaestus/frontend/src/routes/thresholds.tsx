import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/thresholds')({
  component: () => (
    <div style={{ padding: '2rem' }}>
      <h1>Threshold Management</h1>
      <p>Threshold management content will be moved here from App.tsx in subsequent tasks.</p>
    </div>
  ),
});
