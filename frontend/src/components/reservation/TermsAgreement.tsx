import { TERMS_VERSION, HOLD_DURATION_MINUTES } from '../../constants/enums';

/**
 * 동의한 약관 버전은 예약과 함께 저장됩니다.
 * 동의 후 예약은 결제 대기(HOLD) 상태로 만들어지며, 10분 안에 결제하지 않으면
 * 자동으로 만료되어 슬롯이 반환됩니다(core-domain-decisions.md 2-1).
 */
export default function TermsAgreement({
  checked,
  onChange,
  disabled,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <label className="check">
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>
        이용 약관({TERMS_VERSION})과 취소 규정에 동의합니다. 예약 후 {HOLD_DURATION_MINUTES}분 안에
        결제를 완료하지 않으면 자동으로 취소됩니다.
      </span>
    </label>
  );
}
