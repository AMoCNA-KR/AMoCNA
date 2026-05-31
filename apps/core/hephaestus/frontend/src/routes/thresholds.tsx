import { createFileRoute } from '@tanstack/react-router';
import ThresholdsView from '../features/thresholds/ThresholdsView';

export const Route = createFileRoute('/thresholds')({
  component: () => (
    <div style={{ padding: '2rem' }}>
      <ThresholdsView />
    </div>
  ),
});
