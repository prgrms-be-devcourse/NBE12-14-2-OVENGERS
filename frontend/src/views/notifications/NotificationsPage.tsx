'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getNotifications, readAllNotifications } from '../../api/notificationApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import { formatDateTime } from '../../utils/date';
import Pagination from '../../components/common/Pagination';
import EmptyState from '../../components/common/EmptyState';
import ErrorMessage from '../../components/common/ErrorMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import { NOTIFICATIONS_READ_EVENT } from '../../components/notification/NotificationLink';
import styles from '../../components/notification/Notification.module.css';

export default function NotificationsPage() {
  const [page, setPage] = useState(0);
  return <div className={styles.page}>
    <div className="pagehead"><div><h1>내 알림</h1><p>빈자리 알림을 확인하세요. 목록을 열면 모든 알림이 읽음 처리됩니다.</p></div></div>
    <NotificationResults key={page} page={page} onPage={setPage} />
  </div>;
}

function NotificationResults({ page, onPage }: { page: number; onPage: (page: number) => void }) {
  const { data, loading, error, run } = useAsync(() => getNotifications({ page }), [page]);
  const { execute, error: readError } = useAction(async () => {
    await readAllNotifications();
    window.dispatchEvent(new Event(NOTIFICATIONS_READ_EVENT));
  });
  useEffect(() => {
    if (data && !loading && !error) void execute().catch(() => {});
    // 목록 스냅샷이 렌더링된 다음 읽음 처리한다. 읽음 응답으로 data를 덮어쓰지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, loading, error]);
  if (loading) return <LoadingSpinner label="알림을 불러오고 있습니다…" />;
  if (error) return <ErrorMessage error={error} onRetry={() => { void run().catch(() => {}); }} />;
  return <>
    <ErrorMessage error={readError} onRetry={() => { void execute().catch(() => {}); }} />
    {!data?.content.length ? <EmptyState title="아직 받은 알림이 없습니다" description="대기 중인 시간에 빈자리가 나면 여기서 확인할 수 있어요." /> :
      <ul className={styles.list}>{data.content.map((notification) => <li className={styles.item} key={notification.id}>
        <div className={styles.meta}>{!notification.read && <span className={styles.unread}>안 읽음</span>}<time dateTime={notification.createdAt}>{formatDateTime(notification.createdAt)}</time></div>
        <p>{notification.message}</p><Link className="btn small" href={ROUTES.waitlist}>내 대기 보기</Link>
      </li>)}</ul>}
    <Pagination page={data?.page} totalPages={data?.totalPages} totalElements={data?.totalElements} onChange={onPage} />
  </>;
}
