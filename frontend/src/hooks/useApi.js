/**
 * 비동기 호출의 로딩·데이터·오류 상태를 한 번에 다룹니다.
 *
 * useAsync  : 화면에 들어오면 바로 부르는 조회용
 * useAction : 버튼을 눌렀을 때 부르는 실행용 (중복 제출 방지 포함)
 */

import { useCallback, useEffect, useRef, useState } from 'react';

export function useAsync(asyncFn, deps = [], { immediate = true } = {}) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(immediate);
  const [error, setError] = useState(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(async (...args) => {
    setLoading(true);
    setError(null);
    try {
      const result = await asyncFn(...args);
      if (mounted.current) setData(result);
      return result;
    } catch (caught) {
      if (mounted.current) setError(caught);
      throw caught;
    } finally {
      if (mounted.current) setLoading(false);
    }
    // asyncFn 은 매 렌더마다 새로 만들어지므로 의존성에서 제외하고 deps 를 사용합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    if (!immediate) return;
    run().catch(() => {});
  }, [run, immediate]);

  return { data, loading, error, run, setData, setError };
}

export function useAction(asyncFn) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const inFlight = useRef(false);

  const execute = useCallback(
    async (...args) => {
      // 결제가 걸린 요청의 중복 제출을 막습니다. 서버 멱등성 키와 함께 이중으로 방어합니다.
      if (inFlight.current) return undefined;
      inFlight.current = true;
      setLoading(true);
      setError(null);
      try {
        return await asyncFn(...args);
      } catch (caught) {
        setError(caught);
        throw caught;
      } finally {
        inFlight.current = false;
        setLoading(false);
      }
    },
    [asyncFn],
  );

  return { execute, loading, error, setError };
}
