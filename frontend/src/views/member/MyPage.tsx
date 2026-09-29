'use client';

import { getMe } from '@/api/memberApi';
import { useAsync } from '@/hooks/useApi';
import { formatCredit } from '@/utils/price';
import LoadingSpinner from '@/components/common/LoadingSpinner';
import ErrorMessage from '@/components/common/ErrorMessage';

export default function MyPage() {
  const { data: member, loading, error, run } = useAsync(getMe);

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
            <dd style={{ margin: 0 }}>{member.nickname}</dd>

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
        </section>
      )}
    </div>
  );
}