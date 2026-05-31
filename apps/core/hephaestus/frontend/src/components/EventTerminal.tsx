import { useRef, useEffect } from 'react';
import { Terminal } from 'lucide-react';
import type { LogEntry } from '../types/telemetry';
import styles from './EventTerminal.module.css';
import panelStyles from './Panel.module.css';

interface EventTerminalProps {
  logs: LogEntry[];
}

export default function EventTerminal({ logs }: EventTerminalProps) {
  const terminalEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    terminalEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const getTagClass = (tag: string) => {
    const capitalized = tag.charAt(0).toUpperCase() + tag.slice(1);
    const key = `tag${capitalized}` as keyof typeof styles;
    return styles[key] || styles.tagSystem;
  };

  return (
    <div className={panelStyles.panel}>
      <div className={panelStyles.panelHeader}>
        <div>
          <h2>
            <Terminal size={18} style={{ verticalAlign: 'middle', marginRight: '6px' }} /> 
            Live RabbitMQ Ticker
          </h2>
          <p>Real-time event stream from AMoCNA exchange</p>
        </div>
      </div>
      <div className={styles.terminalContainer}>
        {logs.map((log, index) => (
          <div key={index} className={styles.terminalLine}>
            <span className={styles.logTime}>{log.time}</span>
            <span className={`${styles.logTag} ${getTagClass(log.tag)}`}>{log.tag}</span>
            <span className={styles.logMsg} dangerouslySetInnerHTML={{ __html: log.msg }} />
          </div>
        ))}
        <div ref={terminalEndRef} />
      </div>
    </div>
  );
}
