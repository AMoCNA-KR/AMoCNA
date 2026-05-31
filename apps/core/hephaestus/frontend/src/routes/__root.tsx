import { createRootRoute, Outlet } from '@tanstack/react-router';
import { TanStackRouterDevtools } from '@tanstack/router-devtools';
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import { useRealTimeTelemetry } from '../hooks/useRealTimeTelemetry';
import styles from './Layout.module.css';

function RootComponent() {
  useRealTimeTelemetry();
  return (
    <div className={styles.appContainer}>
      <Header />
      <div className={styles.mainLayout}>
        <Sidebar />
        <main className={styles.mainContent}>
          <Outlet />
        </main>
      </div>
      <TanStackRouterDevtools />
    </div>
  );
}

export const Route = createRootRoute({
  component: RootComponent,
});
