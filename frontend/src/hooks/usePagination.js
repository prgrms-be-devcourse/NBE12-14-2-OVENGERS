import { useCallback, useMemo, useState } from 'react';

/** 서버 페이지네이션 응답(content, page, size, totalElements, totalPages)과 함께 사용합니다. */
export function usePagination({ initialPage = 0, initialSize = 10 } = {}) {
  const [page, setPage] = useState(initialPage);
  const [size, setSize] = useState(initialSize);

  const reset = useCallback(() => setPage(initialPage), [initialPage]);

  return useMemo(
    () => ({
      page,
      size,
      setPage,
      setSize,
      reset,
      next: () => setPage((p) => p + 1),
      prev: () => setPage((p) => Math.max(p - 1, 0)),
    }),
    [page, size, reset],
  );
}

export default usePagination;
