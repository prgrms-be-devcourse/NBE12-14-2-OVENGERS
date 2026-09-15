/**
 * 취소·환불 규정 안내. 시작 이후에는 취소할 수 없고(체크아웃으로만 종료),
 * 노쇼(시작+15분 미체크인)는 환불이 없습니다(core-domain-decisions.md 6-1, 6-3).
 *
 * minutesUntilStart 를 넘기면 현재 적용되는 구간을 강조합니다. 화면 표시는 참고용이며
 * 실제 환불액은 취소 요청 시점에 서버가 다시 판단합니다.
 */
export default function CancelRefundInfo({ minutesUntilStart }) {
  const tiers = [
    { key: 'full', label: '시작 1시간 전까지', detail: '전액 환불', min: 60, tone: 'green' },
    { key: 'half', label: '시작 1시간 전 ~ 시작 전', detail: '50% 환불 (위약금 50%)', min: 0, tone: 'cyan' },
    { key: 'none', label: '시작 이후', detail: '취소 불가 (체크아웃으로만 종료)', min: -Infinity, tone: 'red' },
  ];

  const activeKey =
    typeof minutesUntilStart === 'number'
      ? tiers.find((tier) => minutesUntilStart >= tier.min)?.key
      : null;

  return (
    <div className="panel" style={{ marginTop: 16 }}>
      <h3>취소 및 환불 안내</h3>
      {tiers.map((tier) => (
        <div key={tier.key} className="definition">
          <span>
            {tier.label}
            {activeKey === tier.key && <span className={`badge ${tier.tone}`} style={{ marginLeft: 8 }}>현재</span>}
          </span>
          <strong>{tier.detail}</strong>
        </div>
      ))}
      <p className="form-help">노쇼(시작 15분 후까지 미체크인)로 처리되면 환불되지 않습니다.</p>
    </div>
  );
}
