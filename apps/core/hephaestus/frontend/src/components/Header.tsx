import { Flame } from 'lucide-react';
import styles from './Header.module.css';

export default function Header() {
  return (
    <header className={styles.mainHeader}>
      <div className={styles.logoSection}>
        <Flame className={styles.logoIcon} size={28} />
        <div className={styles.logoText}>
          <h1>HEPHAESTUS</h1>
          <span>MAPE-K TELEMETRY & CONTROL PANEL</span>
        </div>
      </div>
      <div className={styles.statusPill}>
        <div className={styles.statusDot}></div>
        <span>Loop Agent Active</span>
      </div>
    </header>
  );
}
