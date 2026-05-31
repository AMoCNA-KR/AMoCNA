import { Link } from '@tanstack/react-router';
import { LayoutDashboard, Sliders, Activity } from 'lucide-react';
import styles from './Sidebar.module.css';

export default function Sidebar() {
  return (
    <aside className={styles.sidebar}>
      <div className={styles.navGroup}>
        <div className={styles.groupTitle}>Navigation</div>
        <Link 
          to="/" 
          activeProps={{ className: styles.activeLink }}
          className={styles.sidebarLink}
          activeOptions={{ exact: true }}
        >
          <LayoutDashboard size={18} />
          <span>Dashboard</span>
        </Link>
        <Link 
          to="/analytics" 
          activeProps={{ className: styles.activeLink }}
          className={styles.sidebarLink}
        >
          <Activity size={18} />
          <span>Analytics</span>
        </Link>
        <Link 
          to="/thresholds" 
          activeProps={{ className: styles.activeLink }}
          className={styles.sidebarLink}
        >
          <Sliders size={18} />
          <span>Thresholds</span>
        </Link>
      </div>
    </aside>
  );
}
