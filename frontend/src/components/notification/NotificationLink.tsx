'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { getUnreadCount } from '../../api/notificationApi';
import { ROUTES } from '../../constants/routePaths';
import UnreadBadge from './UnreadBadge';
import styles from './Notification.module.css';

export const NOTIFICATIONS_READ_EVENT = 'slotkey:notifications-read';

export default function NotificationLink({ memberId, pathname }: { memberId?: number; pathname: string }) {
  const [result, setResult] = useState<{ memberId: number; count: number } | null>(null);
  const onNotifications = pathname === ROUTES.notifications;
  useEffect(() => {
    if (!memberId) return;
    let active = true;
    let request = 0;
    const refresh = () => {
      const current = ++request;
      void getUnreadCount().then(({ unreadCount }) => {
        if (active && current === request) setResult({ memberId, count: unreadCount });
      }).catch(() => {});
    };
    refresh();
    window.addEventListener(NOTIFICATIONS_READ_EVENT, refresh);
    return () => { active = false; window.removeEventListener(NOTIFICATIONS_READ_EVENT, refresh); };
  }, [memberId, onNotifications]);
  if (!memberId) return null;
  return <Link className={styles.entry} href={ROUTES.notifications}>알림<UnreadBadge count={result?.memberId === memberId ? result.count : 0} /></Link>;
}
