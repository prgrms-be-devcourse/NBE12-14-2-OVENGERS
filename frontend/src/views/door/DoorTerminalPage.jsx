'use client';

import { useCallback, useState } from 'react';
import { getSpaces } from '../../api/spaceApi';
import { verifyAccess } from '../../api/doorAccessApi';
import { useAction, useAsync } from '../../hooks/useApi';
import DoorVerifyForm from '../../components/access/DoorVerifyForm';
import DoorVerifyResult from '../../components/access/DoorVerifyResult';
import ErrorMessage from '../../components/common/ErrorMessage';
import KeyGlyph from '../../components/brand/KeyGlyph';

/**
 * 실제 스마트락 대신 사용하는 웹 출입 단말입니다.
 * 허용·거절 판단은 모두 서버가 하며, 이 화면은 결과를 보여주기만 합니다.
 */
export default function DoorTerminalPage() {
  const [form, setForm] = useState({ accessKey: '', spaceId: '' });
  const [result, setResult] = useState(null);

  const fetchSpaces = useCallback(() => getSpaces({ size: 100 }), []);
  const { data: spaceData } = useAsync(fetchSpaces, [fetchSpaces]);

  const { execute, loading, error } = useAction(async () => {
    const response = await verifyAccess({
      token: form.accessKey,
      spaceId: Number(form.spaceId),
    });
    setResult(response);
  });

  return (
    <div className="terminal">
      <div className="terminal-top">
        <div className="terminal-icon" aria-hidden="true">
          <KeyGlyph />
        </div>
        <h1>모의 출입 단말</h1>
        <p>발급받은 출입 키로 출입 가능 여부를 확인합니다.</p>
      </div>

      <DoorVerifyForm
        spaces={spaceData?.content ?? []}
        value={form}
        onChange={(next) => {
          setForm(next);
          setResult(null);
        }}
        onSubmit={() => execute().catch(() => {})}
        loading={loading}
      />

      <ErrorMessage error={error} />
      <DoorVerifyResult result={result} />

      <p className="note">
        예약자 본인, 예약한 공간, 확정 상태, 허용된 시간 네 가지를 모두 만족해야 출입할 수 있습니다.
        거절된 시도도 모두 기록됩니다.
      </p>
    </div>
  );
}
