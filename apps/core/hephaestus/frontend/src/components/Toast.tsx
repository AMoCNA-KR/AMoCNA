import { Bell } from 'lucide-react';
import styles from './Toast.module.css';

interface ToastProps {
  show: boolean;
  title: string;
  body: string;
}

export default function Toast({ show, title, body }: ToastProps) {
  return (
    <div className={`${styles.toastAlert} ${show ? styles.show : ''}`}>
      <Bell className={styles.toastIcon} size={20} />
      <div>
        <h4>{title}</h4>
        <p>{body}</p>
      </div>
    </div>
  );
}

