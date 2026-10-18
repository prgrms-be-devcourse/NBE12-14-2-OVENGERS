'use client';

import Link from 'next/link';
import { useCallback, useState } from 'react';
import {changeMemberStatus, getAdminMemberDetail, getAdminMemberCreditTransactions, grantMemberCredit,} from '../../api/adminMemberApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import { useRouteId } from '../../hooks/useRouteId';
import { ROUTES } from '../../constants/routePaths';
import {MEMBER_ROLE, MEMBER_ROLE_LABEL, MEMBER_STATUS,} from '../../constants/enums';
import type {AdminMember, CreditTransactionType, Meta,} from '../../types/api';
import MemberStatusBadge from '../../components/member/MemberStatusBadge';
import MemberStatusChangeDialog from '../../components/member/MemberStatusChangeDialog';
import CreditGrantDialog from '../../components/member/CreditGrantDialog';
import { MetaBadge } from '../../components/common/Badge';
import Button from '../../components/common/Button';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';
import Toast from '../../components/common/Toast';
import { formatDateTime } from '../../utils/date';
import { formatCredit } from '../../utils/price';

// 크레딧 거래 유형별 표시 정보
const TRANSACTION_META: Record<CreditTransactionType, Meta> = {
    SIGNUP_GRANT: {label: '가입 지급', tone: 'green',},
    ADMIN_GRANT: {label: '관리자 지급', tone: 'green',},
    RESERVATION_CHARGE: {label: '예약 결제', tone: 'cyan',},
    REFUND: {label: '환불', tone: 'green',},
    PENALTY: {label: '위약금', tone: 'red',},
    WITHDRAWAL_EXPIRATION: {label: '탈퇴 잔액 소멸', tone: 'gray',},
    TOP_UP: {label: '크레딧 충전', tone: 'green',},
};

// 크레딧 변동 금액 표시
function formatTransactionAmount(amount: number): string {
    const prefix = amount > 0 ? '+' : '';
    return `${prefix}${formatCredit(amount)}`;
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

    // 크레딧 거래 내역 페이지네이션
    const { page, size, setPage } = usePagination({
        initialSize: 20,
    });

    // 정지/복구 대상 회원
    const [statusTarget, setStatusTarget] =
        useState<AdminMember | null>(null);

    // 크레딧 지급 대상 회원
    const [creditTarget, setCreditTarget] =
        useState<AdminMember | null>(null);

    // 알림 메시지
    const [toast, setToast] = useState<string | null>(null);

    // --------------------------------------------------
    // 회원 상세 조회 API
    // --------------------------------------------------

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

    // --------------------------------------------------
    // 크레딧 거래 내역 조회 API
    // --------------------------------------------------

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

    // --------------------------------------------------
    // 회원 정지 및 복구
    // --------------------------------------------------

    const change = useAction(async (reason: string) => {
        if (!statusTarget) return;

        const nextStatus =
            statusTarget.status === MEMBER_STATUS.ACTIVE
                ? MEMBER_STATUS.SUSPENDED
                : MEMBER_STATUS.ACTIVE;

        await changeMemberStatus(statusTarget.memberId, {
            status: nextStatus,
            reason,
        });

        setStatusTarget(null);

        setToast(
            nextStatus === MEMBER_STATUS.SUSPENDED
                ? '회원을 정지했습니다.'
                : '회원을 복구했습니다.',
        );

        // 변경된 회원 상태 다시 조회
        await reloadMember();
    });

    // --------------------------------------------------
    // 관리자 크레딧 지급
    // --------------------------------------------------

    const grant = useAction(
        async ({
                   amount,
                   reason,
               }: {
            amount: number;
            reason: string;
        }) => {
            if (!creditTarget) return;

            await grantMemberCredit(creditTarget.memberId, {
                amount,
                reason,
            });

            setCreditTarget(null);

            setToast(`${formatCredit(amount)}을 지급했습니다.`);

            // 회원 잔액과 크레딧 내역 갱신
            await Promise.all([
                reloadMember(),
                reloadTransactions(),
            ]);
        },
    );

    // --------------------------------------------------
    // 잘못된 회원 ID
    // --------------------------------------------------

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

    // --------------------------------------------------
    // 회원 조회 로딩 및 오류 처리
    // --------------------------------------------------

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

    // --------------------------------------------------
    // 화면에 사용할 데이터
    // --------------------------------------------------

    const member = detail.member;
    const items = transactions?.content ?? [];

    // 관리자 계정과 탈퇴 계정은 관리 대상에서 제외
    const canManage =
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
                    <p className="page-kicker">
                        MEMBER DETAIL
                    </p>
                    <h1>회원 상세</h1>
                    <div className="row wrap">
                        <strong>{member.nickname}</strong>
                        <MemberStatusBadge status={member.status} />
                    </div>
                    <p>
                        {member.email} · 회원 번호 #{member.memberId}
                    </p>
                </div>

                {/* 관리자 액션 */}
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

            {/* ------------------------------------------------
          요약 통계
      ------------------------------------------------ */}

            <div className="grid3">
                <section className="panel">
                    <h3>보유 크레딧</h3>

                    <p className="price">
                        {formatCredit(member.balance)}
                    </p>

                    <p className="muted">
                        현재 크레딧 잔액
                    </p>
                </section>

                <section className="panel">
                    <h3>전체 예약 건수</h3>

                    <p className="price">
                        {detail.totalReservationCount.toLocaleString('ko-KR')}건
                    </p>

                    <p className="muted">
                        취소 및 만료된 예약 포함
                    </p>
                </section>

                <section className="panel">
                    <h3>정지 발생 횟수</h3>

                    <p className="price">
                        {detail.suspensionCount.toLocaleString('ko-KR')}회
                    </p>

                    <p className="muted">
                        관리자 정지 및 시스템 인증 잠금
                    </p>
                </section>
            </div>

            {/* ------------------------------------------------
          회원 정보
      ------------------------------------------------ */}

            <section className="section">
                <h2>회원 정보</h2>

                <div className="grid2">
                    {/* 기본 정보 */}
                    <div className="panel">
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
                            <strong style={{ overflowWrap: 'anywhere' }}>
                                {member.email}
                            </strong>
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

                    {/* 계정 상태 */}
                    <div className="panel">
                        <h3>계정 상태</h3>

                        <div className="definition">
                            <span>현재 상태</span>
                            <MemberStatusBadge status={member.status} />
                        </div>

                        <div className="definition">
                            <span>최근 상태 변경일</span>
                            <strong>
                                {formatDateTime(member.lastStatusChangedAt) || '-'}
                            </strong>
                        </div>

                        <div className="definition">
                            <span>최근 변경 사유</span>
                            <strong style={{ overflowWrap: 'anywhere' }}>
                                {member.lastStatusChangeReason || '-'}
                            </strong>
                        </div>
                    </div>
                </div>
            </section>

            {/* ------------------------------------------------
          크레딧 거래 내역
      ------------------------------------------------ */}

            <section className="section">
                <h2>크레딧 거래 내역</h2>

                <p>
                    해당 회원의 크레딧 지급, 충전, 예약 결제,
                    환불 내역을 최신순으로 조회합니다.
                </p>

                {/* 거래 내역 로딩 */}
                {transactionsLoading && (
                    <LoadingSpinner
                        label="크레딧 거래 내역을 불러오는 중입니다…"
                    />
                )}

                {/* 거래 내역 오류 */}
                <ErrorMessage
                    error={transactionsError}
                    onRetry={() => {
                        void reloadTransactions().catch(() => {});
                    }}
                />

                {/* 내역이 없는 경우 */}
                {!transactionsLoading &&
                    !transactionsError &&
                    items.length === 0 && (
                        <EmptyState
                            title="크레딧 거래 내역이 없습니다"
                            description="아직 발생한 크레딧 거래가 없습니다."
                        />
                    )}

                {/* 거래 내역 테이블 */}
                {!transactionsLoading &&
                    !transactionsError &&
                    items.length > 0 && (
                        <div className="tablebox" tabIndex={0}>
                            <table>
                                <caption className="sr-only">
                                    회원 크레딧 거래 내역
                                </caption>

                                <thead>
                                <tr>
                                    <th scope="col">거래 번호</th>
                                    <th scope="col">거래 일시</th>
                                    <th scope="col">거래 유형</th>
                                    <th scope="col">변동 금액</th>
                                    <th scope="col">거래 후 잔액</th>
                                    <th scope="col">사유</th>
                                </tr>
                                </thead>

                                <tbody>
                                {items.map((transaction) => (
                                    <tr key={transaction.transactionId}>
                                        <td>
                                            #{transaction.transactionId}
                                        </td>

                                        <td>
                                            {formatDateTime(transaction.createdAt) || '-'}
                                        </td>

                                        <td>
                                            <MetaBadge
                                                meta={
                                                    TRANSACTION_META[transaction.type]
                                                }
                                            />
                                        </td>

                                        <td>
                                            <MetaBadge
                                                meta={{
                                                    label: formatTransactionAmount(
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
                                            {formatCredit(transaction.balanceAfter)}
                                        </td>

                                        <td>
                                            {transaction.reason || '-'}
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                {/* 페이지네이션 */}
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

            {/* 처리 결과 알림 */}
            <Toast
                message={toast}
                onClose={() => setToast(null)}
            />
        </>
    );
}
