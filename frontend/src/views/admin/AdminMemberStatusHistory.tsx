'use client';
import { useCallback } from 'react';
import { getAdminMemberStatusHistory } from '../../api/adminMemberApi';
import { useAsync } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import { MEMBER_STATUS_META } from '../../constants/enums';
import { formatDateTime } from '../../utils/date';
import type {AdminMemberStatusHistoryAction, MemberStatus, Meta,} from '../../types/api';
import { MetaBadge } from '../../components/common/Badge';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';
import styles from './AdminMemberDetailPage.module.css';

const ACTION_META: Record<AdminMemberStatusHistoryAction, Meta> = {
    SUSPEND_MEMBER: {
        label: '관리자 정지',
        tone: 'red',
    },
    REACTIVATE_MEMBER: {
        label: '관리자 복구',
        tone: 'green',
    },
    LOCK_MEMBER_BY_VERIFICATION: {
        label: '인증 실패 잠금',
        tone: 'red',
    },
};

function formatAuditStatus(value: string | null): string {
    if (!value) return '-';

    let result: unknown = value;

    try {
        result = JSON.parse(value);
    } catch {
        // 일반 문자열은 그대로 사용
    }

    if (result === null) return '-';

    if (typeof result === 'string') {
        return MEMBER_STATUS_META[result as MemberStatus]?.label ?? result;
    }

    return typeof result === 'object'
        ? JSON.stringify(result)
        : String(result);
}

export default function AdminMemberStatusHistory({
                                                     memberId,
                                                 }: {
    memberId: number;
}) {
    const { page, size, setPage } = usePagination({
        initialSize: 20,
    });

    const fetchHistory = useCallback(
        () => getAdminMemberStatusHistory(memberId, page, size),
        [memberId, page, size],
    );

    const { data, loading, error, run } = useAsync(
        fetchHistory,
        [fetchHistory],
    );

    const histories = data?.content ?? [];

    return (
        <div className={styles.tabPanel}>
            <div className={styles.tabHeading}>
                <h3 className={styles.tabTitle}>상태 변경 이력</h3>
                <p className={styles.tabDescription}>
                    관리자 정지·복구 및 시스템 인증 실패 잠금 기록을
                    최신순으로 조회합니다.
                </p>
            </div>

            {loading && (
                <LoadingSpinner
                    label="상태 변경 이력을 불러오는 중입니다…"
                />
            )}

            <ErrorMessage
                error={error}
                onRetry={() => {
                    void run().catch(() => {});
                }}
            />

            {!loading && !error && data && histories.length === 0 && (
                <EmptyState
                    title="상태 변경 이력이 없습니다"
                    description="아직 계정 정지 또는 복구 기록이 없습니다."
                />
            )}

            {!loading && !error && histories.length > 0 && (
                <div className="tablebox" tabIndex={0}>
                    <table>
                        <caption className="sr-only">
                            회원 상태 변경 이력
                        </caption>
                        <thead>
                        <tr>
                            <th scope="col">기록 번호</th>
                            <th scope="col">변경 일시</th>
                            <th scope="col">변경 유형</th>
                            <th scope="col">변경 전</th>
                            <th scope="col">변경 후</th>
                            <th scope="col">처리자</th>
                            <th scope="col">사유</th>
                        </tr>
                        </thead>
                        <tbody>
                        {histories.map((history) => (
                            <tr key={history.historyId}>
                                <td>#{history.historyId}</td>
                                <td>
                                    {formatDateTime(history.changedAt) || '-'}
                                </td>
                                <td>
                                    <MetaBadge
                                        meta={ACTION_META[history.action]}
                                        fallback={history.action}
                                    />
                                </td>
                                <td>
                                    {formatAuditStatus(history.beforeValue)}
                                </td>
                                <td>
                                    {formatAuditStatus(history.afterValue)}
                                </td>
                                <td>
                                    {history.actorMemberId === null
                                        ? '시스템'
                                        : `#${history.actorMemberId}`}
                                </td>
                                <td style={{ overflowWrap: 'anywhere' }}>
                                    {history.reason || '-'}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {!loading && !error && data && (
                <Pagination
                    page={data.page}
                    totalPages={data.totalPages}
                    totalElements={data.totalElements}
                    onChange={setPage}
                />
            )}
        </div>
    );
}
