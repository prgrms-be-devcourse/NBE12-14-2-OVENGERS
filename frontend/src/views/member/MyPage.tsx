'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { getMe, updateNickname } from '@/api/memberApi';
import { useAction, useAsync } from '@/hooks/useApi';
import { useAuth } from '@/hooks/useAuth';
import Button from '@/components/common/Button';
import { formatCredit } from '@/utils/price';
import LoadingSpinner from '@/components/common/LoadingSpinner';
import ErrorMessage from '@/components/common/ErrorMessage';
import WithdrawalDialog from '@/components/member/WithdrawalDialog';

export default function MyPage() {
  
    const { data: member, loading, error, run, setData } = useAsync(getMe);
    const { refreshMember } = useAuth();
    
    const [editing, setEditing] = useState(false);
    const [nickname, setNickname] = useState('');
    const [validationError, setValidationError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [withdrawalOpen, setWithdrawalOpen] = useState(false);
    
    const {
      execute,
      loading: saving,
      error: saveError,
      setError: setSaveError,
    } = useAction(updateNickname);
    
    function startEditing() {
      if (!member) return;
    
      setNickname(member.nickname);
      setValidationError(null);
      setSaveError(null);
      setNotice(null);
      setEditing(true);
    }
    
    async function handleSave(event: FormEvent<HTMLFormElement>) {
      event.preventDefault();
      if (saving) return;
    
      const value = nickname.trim();
      setValidationError(null);
      setSaveError(null);
      setNotice(null);
    
      if (value.length < 2 || value.length > 50) {
        setValidationError('닉네임은 2자 이상 50자 이하여야 합니다.');
        return;
      }
    
      try {
        const updatedMember = await execute(value);
        if (!updatedMember) return;
    
        // 수정 응답으로 마이페이지를 즉시 갱신합니다.
        setData(updatedMember);
        setEditing(false);
        setNotice('닉네임이 변경되었습니다.');
      } catch {
        // useAction이 저장한 오류를 화면에서 표시합니다.
        return;
      }
    
      try {
        // 공통 회원 정보를 갱신하여 헤더에도 반영합니다.
        await refreshMember();
      } catch {
        setNotice(
          '닉네임은 변경되었지만 상단 메뉴 갱신에 실패했습니다. 페이지를 새로고침해주세요.',
        );
      }
    }

  return (
    <div className="mypage">
      <div className="pagehead">
        <div>
          <p className="page-kicker">MY PAGE</p>
          <h1>마이페이지</h1>
          <p>내 계정 정보와 보유 크레딧을 확인하세요.</p>
        </div>
      </div>

      {loading && (
        <LoadingSpinner label="내 정보를 불러오고 있습니다…" />
      )}

      <ErrorMessage
        error={error}
        onRetry={() => {
          void run().catch(() => {});
        }}
      />

      {!loading && !error && member && (
        <section className="panel" aria-labelledby="member-info-title">
          <h2 id="member-info-title">내 정보</h2>

          <dl
            style={{
              display: 'grid',
              gridTemplateColumns: '100px minmax(0, 1fr)',
              gap: '16px',
              marginTop: '24px',
              overflowWrap: 'anywhere',
            }}
          >
            <dt>닉네임</dt>
<dd style={{ margin: 0 }}>
  {editing ? (
    <form onSubmit={handleSave}>
      <label htmlFor="mypage-nickname" className="muted">
        새 닉네임
      </label>

      <input
        id="mypage-nickname"
        name="nickname"
        autoComplete="nickname"
        value={nickname}
        onChange={(event) => setNickname(event.target.value)}
        minLength={2}
        maxLength={50}
        required
        disabled={saving}
        aria-invalid={Boolean(validationError)}
        aria-describedby={
          validationError ? 'nickname-validation-error' : undefined
        }
      />

      {validationError && (
        <p
          id="nickname-validation-error"
          className="form-error"
          role="alert"
        >
          {validationError}
        </p>
      )}

      <ErrorMessage error={saveError} />

      <div className="actions wrap" style={{ marginTop: 12 }}>
        <Button type="submit" variant="primary" size="small" loading={saving}>
          저장
        </Button>

        <Button
          size="small"
          disabled={saving}
          onClick={() => setEditing(false)}
        >
          취소
        </Button>
      </div>
    </form>
  ) : (
    <div className="row wrap">
      <span>{member.nickname}</span>
      <Button size="small" onClick={startEditing}>
        수정
      </Button>
    </div>
  )}
</dd>

            <dt>이메일</dt>
            <dd style={{ margin: 0 }}>{member.email}</dd>

            <dt>회원 구분</dt>
            <dd style={{ margin: 0 }}>
              {member.role === 'ADMIN' ? '관리자' : '일반 회원'}
            </dd>

            <dt>보유 크레딧</dt>
            <dd style={{ margin: 0 }}>
              {formatCredit(member.balance)}
            </dd>

            <dt>가입일</dt>
            <dd style={{ margin: 0 }}>
              {member.createdAt.slice(0, 10)}
            </dd>
          </dl>
          {notice && (
  <p className="note" role="status">
    {notice}
  </p>
)}
          {member.role === 'USER' && (
            <div className="section" style={{ marginTop: 24, paddingBottom: 0 }}>
              <h3>회원탈퇴</h3>
              <p>탈퇴 시 계정을 복구할 수 없고 잔여 크레딧이 소멸합니다. 정지 회원은 운영자에게 문의해주세요.</p>
              <Button variant="danger" size="small" disabled={saving || editing}
                onClick={() => setWithdrawalOpen(true)}>회원탈퇴</Button>
            </div>
          )}
          {withdrawalOpen && member.role === 'USER' && (
            <WithdrawalDialog balance={member.balance} onClose={() => setWithdrawalOpen(false)} />
          )}
        </section>
      )}
    </div>
  );
}
