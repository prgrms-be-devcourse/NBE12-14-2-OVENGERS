'use client';

import { useState } from 'react';
import Link from 'next/link';
import { getInquiries } from '../../api/inquiryApi';
import type { InquiryStatus } from '../../api/inquiryApi';
import { useAsync } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import { formatDateTime } from '../../utils/date';
import Select from '../../components/common/Select';
import Pagination from '../../components/common/Pagination';
import EmptyState from '../../components/common/EmptyState';
import ErrorMessage from '../../components/common/ErrorMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import InquiryStatusBadge from '../../components/inquiry/InquiryStatusBadge';
import styles from '../../components/inquiry/Inquiry.module.css';

export default function InquiryListPage({ admin = false }: { admin?: boolean }) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<InquiryStatus | ''>('');
  return <div className={styles.page}>
    <div className={styles.heading}>
      <div><h1>{admin ? '문의 관리' : '내 문의'}</h1>
        <p>{admin ? '회원의 문의를 확인하고 답변을 남겨 주세요.' : '궁금한 점을 문의하고 관리자 답변을 확인하세요.'}</p></div>
      {!admin && <Link href={ROUTES.inquiryNew} className="btn primary">문의 작성</Link>}
    </div>
    {admin && <div className={styles.toolbar}><Select label="답변 상태" value={status}
      options={[{ value: '', label: '전체' }, { value: 'WAITING', label: '답변 대기' }, { value: 'ANSWERED', label: '답변 완료' }]}
      onChange={(event) => { setStatus(event.target.value as InquiryStatus | ''); setPage(0); }} /></div>}
    <InquiryResults key={`${admin}:${page}:${status}`} admin={admin} page={page} status={status} onPage={setPage} />
  </div>;
}

function InquiryResults({ admin, page, status, onPage }: { admin: boolean; page: number; status: InquiryStatus | ''; onPage: (page: number) => void }) {
  const { data, loading, error, run } = useAsync(() => getInquiries({ admin, page, status }), [admin, page, status]);
  if (loading) return <LoadingSpinner label="문의를 불러오고 있습니다…" />;
  if (error) return <ErrorMessage error={error} onRetry={() => { void run().catch(() => {}); }} />;
  return <>
    {!data?.content.length ? <EmptyState title={admin ? '해당하는 문의가 없습니다' : '작성한 문의가 없습니다'}
      description={admin ? '다른 답변 상태를 선택해 보세요.' : '문의 작성 버튼으로 궁금한 점을 남겨 주세요.'} />
      : <ul className={styles.list}>{data.content.map((inquiry) => <li key={inquiry.id}>
        <Link href={admin ? ROUTES.adminInquiryDetail(inquiry.id) : ROUTES.inquiryDetail(inquiry.id)}>
          <div><div className={styles.meta}><InquiryStatusBadge status={inquiry.status} /><span>문의 #{inquiry.id}</span>
            {admin && <span>회원 #{inquiry.memberId}</span>}</div>
            <h2>{inquiry.title}</h2><p>{formatDateTime(inquiry.createdAt)}</p></div><span>상세 보기</span>
        </Link>
      </li>)}</ul>}
    <Pagination page={data?.page} totalPages={data?.totalPages} totalElements={data?.totalElements} onChange={onPage} />
  </>;
}
