import { buildDaySlots } from '../../utils/slot';

/**
 * 운영시간은 30분 단위로만 입력받습니다.
 * 미래 확정 예약과 충돌하는 축소는 서버가 거절합니다(FR-SPACE-09).
 */
const OPTIONS = (() => {
  const times = buildDaySlots('00:00', '24:00').map((slot) => slot.startTime);
  return [...times, '24:00'];
})();

export default function OperatingHoursField({ openingTime, closingTime, onChange, error }) {
  return (
    <>
      <label className="field">
        <span>운영 시작</span>
        <select value={openingTime} onChange={(event) => onChange({ openingTime: event.target.value })}>
          {OPTIONS.slice(0, -1).map((time) => (
            <option key={time} value={time}>
              {time}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>운영 종료</span>
        <select value={closingTime} onChange={(event) => onChange({ closingTime: event.target.value })}>
          {OPTIONS.filter((time) => time > openingTime).map((time) => (
            <option key={time} value={time}>
              {time}
            </option>
          ))}
        </select>
      </label>
      {error && (
        <span className="form-error full" role="alert">
          {error}
        </span>
      )}
    </>
  );
}
