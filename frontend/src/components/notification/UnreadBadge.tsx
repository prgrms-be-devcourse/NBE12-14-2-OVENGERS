import styles from './Notification.module.css';

export default function UnreadBadge({ count }: { count: number }) {
  return count > 0 ? <span className={styles.badge} aria-label={`안 읽은 알림 ${count}개`}>{count}</span> : null;
}
