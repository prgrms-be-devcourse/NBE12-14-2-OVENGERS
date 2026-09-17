import Button from './Button';

/** 서버 응답의 page / totalPages / totalElements 를 그대로 받습니다. */
export default function Pagination({ page = 0, totalPages = 0, totalElements = 0, onChange }) {
  if (totalPages <= 1) {
    return (
      <div className="tablefoot">
        <span>총 {totalElements}건</span>
      </div>
    );
  }

  return (
    <div className="tablefoot">
      <span>
        총 {totalElements}건 · {page + 1} / {totalPages} 페이지
      </span>
      <div className="actions">
        <Button size="small" onClick={() => onChange(page - 1)} disabled={page <= 0}>
          이전
        </Button>
        <Button
          size="small"
          onClick={() => onChange(page + 1)}
          disabled={page >= totalPages - 1}
        >
          다음
        </Button>
      </div>
    </div>
  );
}
