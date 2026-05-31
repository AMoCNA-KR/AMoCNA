import { createFileRoute } from '@tanstack/react-router';
import AnalyticsView from '../features/analytics/AnalyticsView';

export const Route = createFileRoute('/analytics')({
  component: AnalyticsView,
});
