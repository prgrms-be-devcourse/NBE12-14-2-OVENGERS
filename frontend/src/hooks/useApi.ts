/**
 * 비동기 호출의 로딩·데이터·오류 상태를 한 번에 다룹니다.
 *
 * useAsync  : 화면에 들어오면 바로 부르는 조회용
 * useAction : 버튼을 눌렀을 때 부르는 실행용 (중복 제출 방지 포함)
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import type { DependencyList, Dispatch, SetStateAction } from 'react';
import { ApiError } from '../api/client';

/**
 * 화면은 error.code 로 분기하므로 상태를 ApiError 로 통일합니다.
 * api 계층이 던지는 오류는 모두 ApiError 지만, asyncFn 안에서 난 예기치 못한 오류도
 * 같은 모양으로 감싸 둡니다(표시 문구는 기존과 같이 기본 문구로 떨어집니다).
 */
function toApiError(caught: unknown): ApiError {
  if (caught instanceof ApiError) return caught;
  return new ApiError({
    status: 0,
    message: caught instanceof Error ? caught.message : String(caught),
  });
}

export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: ApiError | null;
  run: () => Promise<T>;
  setData: Dispatch<SetStateAction<T | null>>;
  setError: Dispatch<SetStateAction<ApiError | null>>;
}

export function useAsync<T>(
  asyncFn: () => Promise<T>,
  deps: DependencyList = [],
  { immediate = true }: { immediate?: boolean } = {},
): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(immediate);
  const [error, setError] = useState<ApiError | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await asyncFn();
      if (mounted.current) setData(result);
      return result;
    } catch (caught) {
      if (mounted.current) setError(toApiError(caught));
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

export interface ActionState<Args extends unknown[], R> {
  /** 이미 처리 중이면 아무것도 하지 않고 undefined 를 돌려줍니다. */
  execute: (...args: Args) => Promise<R | undefined>;
  loading: boolean;
  error: ApiError | null;
  setError: Dispatch<SetStateAction<ApiError | null>>;
}

export function useAction<Args extends unknown[], R>(
  asyncFn: (...args: Args) => Promise<R>,
): ActionState<Args, R> {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const inFlight = useRef(false);

  const execute = useCallback(
    async (...args: Args) => {
      // 결제가 걸린 요청의 중복 제출을 막습니다. 서버 멱등성 키와 함께 이중으로 방어합니다.
      if (inFlight.current) return undefined;
      inFlight.current = true;
      setLoading(true);
      setError(null);
      try {
        return await asyncFn(...args);
      } catch (caught) {
        setError(toApiError(caught));
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
