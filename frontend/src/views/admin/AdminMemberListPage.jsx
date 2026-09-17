'use client';

import { useCallback, useState } from 'react';
import { changeMemberStatus, getAdminMembers, grantMemberCredit } from '../../api/adminMemberApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import { MEMBER_ROLE, MEMBER_ROLE_LABEL, MEMBER_STATUS, MEMBER_STATUS_OPTIONS } from '../../constants/enums';
import { formatDateTime } from '../../utils/date';
import { formatCredit } from '../../utils/price';
import MemberStatusBadge from '../../components/member/MemberStatusBadge';
import MemberStatusChangeDialog from '../../components/member/MemberStatusChangeDialog';
import CreditGrantDialog from '../../components/member/CreditGrantDialog';
import Select from '../../components/common/Select';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';
import Toast from '../../components/common/Toast';

export default function AdminMemberListPage() {
  const [filter, setFilter] = useState({ status: '', keyword: '' });
  const [target, setTarget] = useState(null);
  const [creditTarget, setCreditTarget] = useState(null);
  const [toast, setToast] = useState(null);
  const { page, size, setPage } = usePagination({ initialSize: 20 });

  const fetchMembers = useCallback(
    () => getAdminMembers({ page, size, ...filter }),
    [page, size, filter],
  );
  const { data, loading, error, run: reload } = useAsync(fetchMembers, [fetchMembers]);

  const change = useAction(async (reason) => {
    const nextStatus =
      target.status === MEMBER_STATUS.ACTIVE ? MEMBER_STATUS.SUSPENDED : MEMBER_STATUS.ACTIVE;
    await changeMemberStatus(target.memberId, { status: nextStatus, reason });
    setToast(
      nextStatus === MEMBER_STATUS.SUSPENDED
        ? '계정을 정지했습니다. 기존 예약과 결제는 유지됩니다.'
        : '계정을 복구했습니다.',
    );
    setTarget(null);
    await reload();
  });

  const grant = useAction(async ({ amount, reason }) => {
    await grantMemberCredit(creditTarget.memberId, { amount, reason });
    setToast(`${creditTarget.nickname}님에게 ${formatCredit(amount)}을 지급했습니다.`);
    setCreditTarget(null);
    await reload();
  });

  const members = data?.content ?? [];

  return (
    <>
      <div className="pagehead">
        <div>
          <h1>회원 관리</h1>
          <p>일반 회원만 정지·복구하거나 크레딧을 지급할 수 있습니다. 관리자 계정은 대상이 아닙니다.</p>
        </div>
      </div>

      <div className="filters">
        <Input
          label="검색"
          placeholder="이메일 또는 닉네임"
          value={filter.keyword}
          onChange={(event) => {
            setFilter({ ...filter, keyword: event.target.value });
            setPage(0);
          }}
        />
        <Select
          label="상태"
          options={MEMBER_STATUS_OPTIONS}
          value={filter.status}
          onChange={(event) => {
            setFilter({ ...filter, status: event.target.value });
            setPage(0);
          }}
        />
      </div>

      {loading && <LoadingSpinner />}
      <ErrorMessage error={error} onRetry={reload} />

      {!loading && !error && members.length === 0 && (
        <EmptyState title="조건에 맞는 회원이 없습니다" description="검색어나 상태를 바꿔 보세요." />
      )}

      {members.length > 0 && (
        <>
          <div className="tablebox">
            <table>
              <caption className="sr-only">회원 목록</caption>
              <thead>
                <tr>
                  <th scope="col">회원</th>
                  <th scope="col">역할</th>
                  <th scope="col">상태</th>
                  <th scope="col">크레딧</th>
                  <th scope="col">가입일</th>
                  <th scope="col">최근 상태 변경</th>
                  <th scope="col">관리</th>
                </tr>
              </thead>
              <tbody>
                {members.map((member) => (
                  <tr key={member.memberId}>
                    <td>
                      {member.nickname}
                      <small>{member.email}</small>
                    </td>
                    <td>{MEMBER_ROLE_LABEL[member.role] ?? member.role}</td>
                    <td>
                      <MemberStatusBadge status={member.status} />
                    </td>
                    <td>{formatCredit(member.balance)}</td>
                    <td>{formatDateTime(member.createdAt)}</td>
                    <td>
                      {member.lastStatusChangedAt ? formatDateTime(member.lastStatusChangedAt) : '-'}
                      {member.lastStatusChangeReason && <small>{member.lastStatusChangeReason}</small>}
                    </td>
                    <td>
                      {member.role === MEMBER_ROLE.ADMIN ? (
                        <span className="muted">-</span>
                      ) : (
                        <div className="row wrap">
                          <Button size="small" onClick={() => setCreditTarget(member)}>
                            크레딧 지급
                          </Button>
                          <Button
                            size="small"
                            variant={member.status === MEMBER_STATUS.ACTIVE ? 'danger' : 'default'}
                            onClick={() => setTarget(member)}
                          >
                            {member.status === MEMBER_STATUS.ACTIVE ? '정지' : '복구'}
                          </Button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={setPage}
          />
        </>
      )}

      <MemberStatusChangeDialog
        member={target}
        loading={change.loading}
        error={change.error}
        onClose={() => setTarget(null)}
        onConfirm={(reason) => change.execute(reason).catch(() => {})}
      />

      <CreditGrantDialog
        member={creditTarget}
        loading={grant.loading}
        error={grant.error}
        onClose={() => setCreditTarget(null)}
        onConfirm={(payload) => grant.execute(payload).catch(() => {})}
      />

      <Toast message={toast} onClose={() => setToast(null)} />
    </>
  );
}
