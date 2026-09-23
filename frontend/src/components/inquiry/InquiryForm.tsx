'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import type { InquiryInput } from '../../api/inquiryApi';
import Input from '../common/Input';
import Button from '../common/Button';
import styles from './Inquiry.module.css';

export default function InquiryForm({ initial, answer = false, loading, onSubmit, onCancel }: {
  initial?: InquiryInput;
  answer?: boolean;
  loading: boolean;
  onSubmit: (input: InquiryInput) => Promise<void>;
  onCancel?: () => void;
}) {
  const [title, setTitle] = useState(initial?.title ?? '');
  const [content, setContent] = useState(initial?.content ?? '');
  const [validation, setValidation] = useState('');
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (loading) return;
    if ((!answer && !title.trim()) || !content.trim()) {
      setValidation(answer ? '답변 내용을 입력해 주세요.' : '제목과 내용을 모두 입력해 주세요.');
      return;
    }
    if (title.length > 200 || content.length > 2000) {
      setValidation('제목은 200자, 내용은 2,000자 이내로 입력해 주세요.');
      return;
    }
    setValidation('');
    await onSubmit({ title: title.trim(), content: content.trim() });
  };
  return <form className={styles.form} onSubmit={submit}>
    {!answer && <Input label="제목" required maxLength={200} value={title} disabled={loading}
      onChange={(event) => setTitle(event.target.value)} help={`${title.length}/200자`} />}
    <label className="field">
      <span>{answer ? '답변 내용' : '문의 내용'} *</span>
      <textarea required maxLength={2000} rows={9} value={content} disabled={loading}
        onChange={(event) => setContent(event.target.value)}
        placeholder={answer ? '회원에게 안내할 내용을 작성해 주세요.' : '궁금한 점과 발생한 상황을 자세히 알려 주세요.'} />
      <span className="form-help">{content.length.toLocaleString()}/2,000자</span>
    </label>
    {validation && <p role="alert" className="form-error">{validation}</p>}
    <div className={styles.actions}>
      {onCancel && <Button onClick={onCancel} disabled={loading}>취소</Button>}
      <Button type="submit" variant="primary" loading={loading}>
        {answer ? '답변 등록' : initial ? '수정 저장' : '문의 등록'}
      </Button>
    </div>
  </form>;
}
