'use client';
import { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { answerInquiry, getInquiry, updateInquiry } from '../../api/inquiryApi';
import type { InquiryInput } from '../../api/inquiryApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import { formatDateTime } from '../../utils/date';
import InquiryForm from '../../components/inquiry/InquiryForm';
import InquiryStatusBadge from '../../components/inquiry/InquiryStatusBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import Button from '../../components/common/Button';
import styles from '../../components/inquiry/Inquiry.module.css';

export default function InquiryDetailPage({ admin = false }: { admin?: boolean }) {
  const id = useSearchParams().get('id') ?? '';
  if (!/^[1-9]\d*$/.test(id)) return <div className={styles.page}>
    <ErrorMessage error="문의 번호가 올바르지 않습니다." />
    <Link className={styles.back} href={admin ? ROUTES.adminInquiries : ROUTES.inquiries}>문의 목록으로 돌아가기</Link>
  </div>;
  return <InquiryDetail key={`${admin}:${id}`} id={id} admin={admin} />;
}

function InquiryDetail({ id, admin }: { id: string; admin: boolean }) {
  const { data, loading, error, run, setData } = useAsync(() => getInquiry(id, admin), [id, admin]);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState('');
  const save = useAction(async (input: InquiryInput) => admin ? answerInquiry(id, input.content) : updateInquiry(id, input));
  const submit = async (input: InquiryInput) => {
    try {
      const result = await save.execute(input);
      if (result) { setData(result); setEditing(false); setNotice(admin ? '답변을 등록했습니다.' : '문의를 수정했습니다.'); }
    } catch (caught) {
      // Another administrator may have answered while the member was editing.
      if (caught && typeof caught === 'object' && 'code' in caught && caught.code === 'INQUIRY_ALREADY_ANSWERED') {
        setEditing(false);
        void run().catch(() => {});
      }
    }
  };
  return <div className={styles.page}>
    <Link href={admin ? ROUTES.adminInquiries : ROUTES.inquiries} className={styles.back}>문의 목록으로 돌아가기</Link>
    <h1>{admin ? '문의 확인 및 답변' : '문의 상세'}</h1>
    {loading && <LoadingSpinner />}
    <ErrorMessage error={error?.code === 'FORBIDDEN_NOT_OWNER' ? '본인이 작성한 문의만 확인할 수 있습니다.' : error}
      onRetry={() => { void run().catch(() => {}); }} />
    <ErrorMessage error={save.error} />
    {notice && <p className={styles.success} role="status">{notice}</p>}
    {!loading && !error && data && <>
      <section className={styles.panel} aria-label="문의 내용">
        <div className={styles.meta}><InquiryStatusBadge status={data.status} /><span>문의 #{data.id}</span>
          {admin && <span>회원 #{data.memberId}</span>}<time dateTime={data.createdAt}>{formatDateTime(data.createdAt)}</time></div>
        {editing && data.status === 'WAITING' ? <InquiryForm initial={{ title: data.title, content: data.content }}
          loading={save.loading} onSubmit={submit} onCancel={() => { setEditing(false); save.setError(null); }} /> : <>
          <h2>{data.title}</h2><p className={styles.body}>{data.content}</p>
          {!admin && data.status === 'WAITING' && <div className={styles.actions}><Button onClick={() => { setEditing(true); setNotice(''); save.setError(null); }}>문의 수정</Button></div>}
        </>}
      </section>
      {data.status === 'ANSWERED' ? <section className={`${styles.panel} ${styles.answer}`} aria-label="관리자 답변">
        <h2>관리자 답변</h2>{data.answeredAt && <time dateTime={data.answeredAt}>{formatDateTime(data.answeredAt)}</time>}
        <p className={styles.body}>{data.answerContent}</p>
        <p className={styles.notice}>답변이 등록된 문의는 수정할 수 없습니다.</p>
      </section> : admin ? <section className={styles.panel} aria-label="답변 작성"><h2>답변 작성</h2>
        <p className={styles.notice}>문의당 답변은 한 번 등록할 수 있습니다. 등록 전 내용을 확인해 주세요.</p>
        <InquiryForm answer loading={save.loading} onSubmit={submit} />
      </section> : <p className={styles.notice}>관리자 답변을 기다리고 있습니다. 답변이 등록되면 이 화면에서 확인할 수 있습니다.</p>}
    </>}
  </div>;
}
