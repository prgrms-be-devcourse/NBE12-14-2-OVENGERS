'use client';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { createInquiry } from '../../api/inquiryApi';
import { useAction } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import InquiryForm from '../../components/inquiry/InquiryForm';
import ErrorMessage from '../../components/common/ErrorMessage';
import styles from '../../components/inquiry/Inquiry.module.css';

export default function InquiryNewPage() {
  const router = useRouter();
  const create = useAction(createInquiry);
  return <div className={styles.page}>
    <Link href={ROUTES.inquiries} className={styles.back}>내 문의로 돌아가기</Link>
    <h1>문의 작성</h1><p>등록한 문의는 본인과 관리자만 확인할 수 있습니다.</p>
    <div className={styles.panel}>
      <p className={styles.notice}>답변이 등록되기 전까지 수정할 수 있습니다. 비밀번호나 출입키는 내용에 포함하지 마세요.</p>
      <ErrorMessage error={create.error} />
      <InquiryForm loading={create.loading} onSubmit={async (input) => {
        try { const created = await create.execute(input); if (created) router.replace(ROUTES.inquiryDetail(created.id)); }
        catch { /* Keep the draft and show the API error. */ }
      }} onCancel={() => router.push(ROUTES.inquiries)} />
    </div>
  </div>;
}
