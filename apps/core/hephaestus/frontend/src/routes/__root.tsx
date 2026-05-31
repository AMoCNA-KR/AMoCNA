import { createRootRoute, Link, Outlet } from '@tanstack/react-router';
import { TanStackRouterDevtools } from '@tanstack/router-devtools';
import Header from '../components/Header';

export const Route = createRootRoute({
  component: () => (
    <div className="app-container">
      <Header />
      <nav className="main-nav" style={{ padding: '1rem', display: 'flex', gap: '1rem', background: 'rgba(255,255,255,0.05)', borderBottom: '1px solid var(--border-glass)' }}>
        <Link 
          to="/" 
          activeProps={{ style: { fontWeight: 'bold', color: 'var(--c-analyze)' } }}
          style={{ color: 'var(--text-dim)', textDecoration: 'none' }}
        >
          Dashboard
        </Link>
        <Link 
          to="/thresholds" 
          activeProps={{ style: { fontWeight: 'bold', color: 'var(--c-analyze)' } }}
          style={{ color: 'var(--text-dim)', textDecoration: 'none' }}
        >
          Thresholds
        </Link>
      </nav>
      <main>
        <Outlet />
      </main>
      <TanStackRouterDevtools />
    </div>
  ),
});
