
'use client';

import Link from 'next/link';
import { useCallback, useState } from 'react';
import {
    changeMemberStatus,
    getAdminMemberDetail,
    getAdminMemberCreditTransactions,
    grantMemberCredit,
} from '../../api/adminMemberApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import { useRouteId } from '../../hooks/useRouteId';
import { ROUTES } from '../../constants/routePaths';
import {
    MEMBER_ROLE,
    MEMBER_ROLE_LABEL,
    MEMBER_STATUS,
} from '../../constants/enums';
import type {
    AdminMember,
    CreditTransactionType,
    Meta,
} from '../../types/api';
import MemberStatusBadge from '../../components/member/MemberStatusBadge';
import MemberStatusChangeDialog from '../../components/member/MemberStatusChangeDialog';
import CreditGrantDialog from '../../components/member/CreditGrantDialog';
import AdminMemberReservations from './AdminMemberReservations';
import AdminMemberStatusHistory from './AdminMemberStatusHistory';
import { MetaBadge } from '../../components/common/Badge';
import Button from '../../components/common/Button';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';
import Toast from '../../components/common/Toast';
import { formatDateTime } from '../../utils/date';
import { formatCredit } from '../../utils/price';
import styles from './AdminMemberDetailPage.module.css';

type MemberDetailTab =
    | 'credits'
    | 'reservations'
    | 'statusHistory';

const TRANSACTION_META: Record<CreditTransactionType, Meta> = {
    SIGNUP_GRANT: { label: '가입 지급', tone: 'green' },
    ADMIN_GRANT: { label: '관리자 지급', tone: 'green' },
    RESERVATION_CHARGE: { label: '예약 결제', tone: 'cyan' },
    REFUND: { label: '환불', tone: 'green' },
    PENALTY: { label: '위약금', tone: 'red' },
    WITHDRAWAL_EXPIRATION: {
        label: '탈퇴 잔액 소멸',
        tone: 'gray',
    },
    TOP_UP: { label: '크레딧 충전', tone: 'green' },
};

function formatTransactionAmount(amount: number): string {
    return `${amount > 0 ? '+' : ''}${formatCredit(amount)}`;
}

export default function AdminMemberDetailPage() {
    const rawMemberId = useRouteId('memberId');

    return (
        <AdminMemberDetailContent
            key={rawMemberId}
            memberId={Number(rawMemberId)}
        />
    );
}

function AdminMemberDetailContent({
                                      memberId,
                                  }: {
    memberId: number;
}) {
    const validMemberId =
        Number.isSafeInteger(memberId) && memberId > 0;

    const [activeTab, setActiveTab] =
        useState<MemberDetailTab>('credits');

    const [historyVersion, setHistoryVersion] = useState(0);

    const [statusTarget, setStatusTarget] =
        useState<AdminMember | null>(null);

    const [creditTarget, setCreditTarget] =
        useState<AdminMember | null>(null);

    const [toast, setToast] = useState<string | null>(null);

    // 변경 API 성공 후 최신 데이터 조회 상태
    const [refreshPending, setRefreshPending] = useState(false);
    const [refreshFailed, setRefreshFailed] = useState(false);

    // 크레딧 거래 내역 페이지네이션
    const { page, size, setPage } = usePagination({
        initialSize: 20,
    });

    // 회원 상세 조회
    const fetchMember = useCallback(
        () => getAdminMemberDetail(memberId),
        [memberId],
    );

    const {
        data: detail,
        loading: memberLoading,
        error: memberError,
        run: reloadMember,
    } = useAsync(
        fetchMember,
        [fetchMember],
        { immediate: validMemberId },
    );

    // 크레딧 거래 내역 조회
    const fetchTransactions = useCallback(
        () => getAdminMemberCreditTransactions(memberId, page, size),
        [memberId, page, size],
    );

    const {
        data: transactions,
        loading: transactionsLoading,
        error: transactionsError,
        run: reloadTransactions,
    } = useAsync(
        fetchTransactions,
        [fetchTransactions],
        { immediate: validMemberId },
    );

    // 회원 정지 및 복구
    const change = useAction(async (reason: string) => {
        if (!statusTarget || refreshPending || refreshFailed) return;

        const nextStatus =
            statusTarget.status === MEMBER_STATUS.ACTIVE
                ? MEMBER_STATUS.SUSPENDED
                : MEMBER_STATUS.ACTIVE;

        // 1. 실제 회원 상태 변경
        // 이 요청이 실패하면 아래 성공 처리로 진행하지 않음
        await changeMemberStatus(statusTarget.memberId, {
            status: nextStatus,
            reason,
        });

        // 2. 변경 API 성공 확정
        setStatusTarget(null);

        setToast(
            nextStatus === MEMBER_STATUS.SUSPENDED
                ? '회원을 정지했습니다.'
                : '회원을 복구했습니다.',
        );

        // 3. 변경 이력 탭도 최신 데이터를 조회하도록 갱신
        setHistoryVersion((version) => version + 1);

        // 4. 회원 상세 정보 재조회
        // 조회 실패는 이미 성공한 정지/복구 작업과 분리
        setRefreshPending(true);

        try {
            await reloadMember();
        } catch {
            setRefreshFailed(true);
        } finally {
            setRefreshPending(false);
        }
    });

    // 관리자 크레딧 지급
    const grant = useAction(
        async ({
                   amount,
                   reason,
               }: {
            amount: number;
            reason: string;
        }) => {
            if (!creditTarget || refreshPending || refreshFailed) return;

            // 1. 실제 크레딧 지급
            await grantMemberCredit(creditTarget.memberId, {
                amount,
                reason,
            });

            // 2. 지급 API 성공 확정
            setCreditTarget(null);
            setToast(`${formatCredit(amount)}을 지급했습니다.`);

            // 3. 회원 잔액 및 거래 내역 재조회
            setRefreshPending(true);

            try {
                const results = await Promise.allSettled([
                    reloadMember(),
                    reloadTransactions(),
                ]);

                // 둘 중 하나라도 실패하면 최신 화면을 보장할 수 없음
                if (results.some(
                    (result) => result.status === 'rejected',
                )) {
                    setRefreshFailed(true);
                }
            } finally {
                setRefreshPending(false);
            }
        },
    );

    // 잘못된 회원 ID
    if (!validMemberId) {
        return (
            <EmptyState
                title="올바르지 않은 회원 번호입니다"
                description="회원 목록에서 다시 선택해 주세요."
                action={
                    <Link
                        href={ROUTES.adminMembers}
                        className="btn primary"
                    >
                        회원 목록으로
                    </Link>
                }
            />
        );
    }

    if (memberLoading && !detail) {
        return (
            <LoadingSpinner label="회원 정보를 불러오는 중입니다…" />
        );
    }

    if (memberError && !detail) {
        return (
            <>
                <nav className="crumb" aria-label="현재 위치">
                    <Link href={ROUTES.adminMembers}>
                        회원 관리
                    </Link>
                    <span>›</span>
                    <span>회원 상세</span>
                </nav>

                <ErrorMessage
                    error={memberError}
                    onRetry={() => {
                        void reloadMember().catch(() => {});
                    }}
                />
            </>
        );
    }

    if (!detail) return null;

    const member = detail.member;
    const transactionsList = transactions?.content ?? [];

    // 최신 데이터가 확인되지 않으면 추가 관리 작업 차단
    const canManage =
        !refreshPending &&
        !refreshFailed &&
        member.role !== MEMBER_ROLE.ADMIN &&
        member.status !== MEMBER_STATUS.WITHDRAWN;

    return (
        <>
            {/* 현재 위치 */}
            <nav className="crumb" aria-label="현재 위치">
                <Link href={ROUTES.adminMembers}>
                    회원 관리
                </Link>
                <span>›</span>
                <span>{member.nickname}</span>
            </nav>

            {/* 회원 상세 헤더 */}
            <div className="pagehead">
                <div>
                    <p className="page-kicker">MEMBER DETAIL</p>
                    <h1>회원 상세</h1>

                    <div className="row wrap">
                        <strong>{member.nickname}</strong>
                        <MemberStatusBadge status={member.status} />
                    </div>

                    <p>
                        {member.email} · 회원 번호 #{member.memberId}
                    </p>
                </div>

                {canManage && (
                    <div className="row wrap">
                        <Button
                            onClick={() => setCreditTarget(member)}
                        >
                            크레딧 지급
                        </Button>

                        <Button
                            variant={
                                member.status === MEMBER_STATUS.ACTIVE
                                    ? 'danger'
                                    : 'default'
                            }
                            onClick={() => setStatusTarget(member)}
                        >
                            {member.status === MEMBER_STATUS.ACTIVE
                                ? '회원 정지'
                                : '회원 복구'}
                        </Button>
                    </div>
                )}
            </div>

            {/* 변경 후 재조회 진행 안내 */}
            {refreshPending && (
                <p className="muted" role="status">
                    변경 사항을 반영한 최신 정보를 불러오는 중입니다…
                </p>
            )}

            {/* 변경 성공 후 재조회 실패 안내 */}
            {refreshFailed && (
                <div
                    className="form-error"
                    role="alert"
                    style={{ marginBottom: 20 }}
                >
                    <p>
                        변경 작업은 완료됐지만 최신 정보를
                        불러오지 못했습니다.
                        중복 작업을 방지하기 위해 관리 버튼을
                        일시적으로 숨겼습니다.
                    </p>

                    <div
                        className="actions"
                        style={{ marginTop: 12 }}
                    >
                        <Button
                            size="small"
                            onClick={() => window.location.reload()}
                        >
                            새로고침
                        </Button>
                    </div>
                </div>
            )}

            {/* 요약 통계 */}
            <div className={`grid3 ${styles.summaryGrid}`}>
                <section className={`panel ${styles.summaryCard}`}>
                    <h3>보유 크레딧</h3>
                    <p className="price">
                        {formatCredit(member.balance)}
                    </p>
                    <p className="muted">
                        현재 크레딧 잔액
                    </p>
                </section>

                <section className={`panel ${styles.summaryCard}`}>
                    <h3>전체 예약 건수</h3>
                    <p className="price">
                        {detail.totalReservationCount.toLocaleString('ko-KR')}건
                    </p>
                    <p className="muted">
                        취소 및 만료된 예약 포함
                    </p>
                </section>

                <section className={`panel ${styles.summaryCard}`}>
                    <h3>정지 기록 건수</h3>
                    <p className="price">
                        {detail.suspensionCount.toLocaleString('ko-KR')}건
                    </p>
                    <p className="muted">
                        관리자 정지 및 시스템 인증 잠금 기록
                    </p>
                </section>
            </div>

            {/* 회원 정보 */}
            <section className="section">
                <h2>회원 정보</h2>

                <div className={`grid2 ${styles.infoGrid}`}>
                    <div className={`panel ${styles.infoCard}`}>
                        <h3>기본 정보</h3>

                        <div className="definition">
                            <span>회원 번호</span>
                            <strong>#{member.memberId}</strong>
                        </div>

                        <div className="definition">
                            <span>닉네임</span>
                            <strong>{member.nickname}</strong>
                        </div>

                        <div className="definition">
                            <span>이메일</span>
                            <strong>{member.email}</strong>
                        </div>

                        <div className="definition">
                            <span>역할</span>
                            <strong>
                                {MEMBER_ROLE_LABEL[member.role] ?? member.role}
                            </strong>
                        </div>

                        <div className="definition">
                            <span>가입일</span>
                            <strong>
                                {formatDateTime(member.createdAt) || '-'}
                            </strong>
                        </div>
                    </div>

                    <div className={`panel ${styles.infoCard}`}>
                        <h3>계정 상태</h3>

                        <div className="definition">
                            <span>현재 상태</span>
                            <MemberStatusBadge status={member.status} />
                        </div>

                        <div className="definition">
                            <span>최근 상태 변경일</span>
                            <strong>
                                {formatDateTime(
                                    member.lastStatusChangedAt,
                                ) || '-'}
                            </strong>
                        </div>

                        <div className="definition">
                            <span>최근 변경 사유</span>
                            <strong>
                                {member.lastStatusChangeReason || '-'}
                            </strong>
                        </div>
                    </div>
                </div>
            </section>

            {/* 회원 이용 내역 */}
            <section className="section">
                <h2>회원 이용 내역</h2>
                <p>
                    크레딧 거래, 예약 및 계정 상태 변경 이력을
                    확인할 수 있습니다.
                </p>

                {/* 기존 프로젝트 tabs 스타일 재사용 */}
                <div
                    className={`tabs ${styles.tabs}`}
                    aria-label="회원 이용 내역 선택"
                >
                    <button
                        type="button"
                        className={
                            activeTab === 'credits' ? 'active' : ''
                        }
                        aria-pressed={activeTab === 'credits'}
                        onClick={() => setActiveTab('credits')}
                    >
                        크레딧 거래 내역
                    </button>

                    <button
                        type="button"
                        className={
                            activeTab === 'reservations' ? 'active' : ''
                        }
                        aria-pressed={activeTab === 'reservations'}
                        onClick={() => setActiveTab('reservations')}
                    >
                        예약 내역
                    </button>

                    <button
                        type="button"
                        className={
                            activeTab === 'statusHistory' ? 'active' : ''
                        }
                        aria-pressed={activeTab === 'statusHistory'}
                        onClick={() => setActiveTab('statusHistory')}
                    >
                        상태 변경 이력
                    </button>
                </div>

                {/* 크레딧 거래 내역 */}
                {activeTab === 'credits' && (
                    <div className={styles.tabPanel}>
                        <div className={styles.tabHeading}>
                            <h3 className={styles.tabTitle}>
                                크레딧 거래 내역
                            </h3>
                            <p className={styles.tabDescription}>
                                크레딧 지급, 충전, 예약 결제,
                                환불 내역을 최신순으로 조회합니다.
                            </p>
                        </div>

                        {transactionsLoading && (
                            <LoadingSpinner
                                label="크레딧 거래 내역을 불러오는 중입니다…"
                            />
                        )}

                        <ErrorMessage
                            error={transactionsError}
                            onRetry={() => {
                                void reloadTransactions().catch(() => {});
                            }}
                        />

                        {!transactionsLoading &&
                            !transactionsError &&
                            transactions &&
                            transactionsList.length === 0 && (
                                <EmptyState
                                    title="크레딧 거래 내역이 없습니다"
                                    description="아직 발생한 크레딧 거래가 없습니다."
                                />
                            )}

                        {!transactionsLoading &&
                            !transactionsError &&
                            transactionsList.length > 0 && (
                                <div className="tablebox" tabIndex={0}>
                                    <table>
                                        <caption className="sr-only">
                                            회원 크레딧 거래 내역
                                        </caption>

                                        <thead>
                                        <tr>
                                            <th scope="col">
                                                거래 번호
                                            </th>
                                            <th scope="col">
                                                거래 일시
                                            </th>
                                            <th scope="col">
                                                거래 유형
                                            </th>
                                            <th scope="col">
                                                변동 금액
                                            </th>
                                            <th scope="col">
                                                거래 후 잔액
                                            </th>
                                            <th scope="col">
                                                사유
                                            </th>
                                        </tr>
                                        </thead>

                                        <tbody>
                                        {transactionsList.map(
                                            (transaction) => (
                                                <tr
                                                    key={
                                                        transaction.transactionId
                                                    }
                                                >
                                                    <td>
                                                        #{transaction.transactionId}
                                                    </td>
                                                    <td>
                                                        {formatDateTime(
                                                            transaction.createdAt,
                                                        ) || '-'}
                                                    </td>
                                                    <td>
                                                        <MetaBadge
                                                            meta={
                                                                TRANSACTION_META[
                                                                    transaction.type
                                                                    ]
                                                            }
                                                        />
                                                    </td>
                                                    <td>
                                                        <MetaBadge
                                                            meta={{
                                                                label:
                                                                    formatTransactionAmount(
                                                                        transaction.amount,
                                                                    ),
                                                                tone:
                                                                    transaction.amount > 0
                                                                        ? 'green'
                                                                        : transaction.amount < 0
                                                                            ? 'red'
                                                                            : 'gray',
                                                            }}
                                                        />
                                                    </td>
                                                    <td>
                                                        {formatCredit(
                                                            transaction.balanceAfter,
                                                        )}
                                                    </td>
                                                    <td>
                                                        {transaction.reason || '-'}
                                                    </td>
                                                </tr>
                                            ),
                                        )}
                                        </tbody>
                                    </table>
                                </div>
                            )}

                        {!transactionsLoading &&
                            !transactionsError &&
                            transactions && (
                                <Pagination
                                    page={transactions.page}
                                    totalPages={transactions.totalPages}
                                    totalElements={transactions.totalElements}
                                    onChange={setPage}
                                />
                            )}
                    </div>
                )}

                {/* 예약 내역 */}
                {activeTab === 'reservations' && (
                    <AdminMemberReservations memberId={memberId} />
                )}

                {/* 상태 변경 이력 */}
                {activeTab === 'statusHistory' && (
                    <AdminMemberStatusHistory
                        key={historyVersion}
                        memberId={memberId}
                    />
                )}
            </section>

            {/* 회원 정지/복구 팝업 */}
            <MemberStatusChangeDialog
                member={statusTarget}
                loading={change.loading}
                error={change.error}
                onClose={() => setStatusTarget(null)}
                onConfirm={(reason) => {
                    void change.execute(reason).catch(() => {});
                }}
            />

            {/* 크레딧 지급 팝업 */}
            <CreditGrantDialog
                member={creditTarget}
                loading={grant.loading}
                error={grant.error}
                onClose={() => setCreditTarget(null)}
                onConfirm={(payload) => {
                    void grant.execute(payload).catch(() => {});
                }}
            />

            <Toast
                message={toast}
                onClose={() => setToast(null)}
            />
        </>
    );
}
