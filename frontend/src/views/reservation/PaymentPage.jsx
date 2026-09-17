'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { getMyReservation, payReservation } from '../../api/reservationApi';
import { getSpace } from '../../api/spaceApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { useAuth } from '../../hooks/useAuth';
import { useIdempotencyKey } from '../../hooks/useIdempotencyKey';
import { RESERVATION_STATUS } from '../../constants/enums';
import { ROUTES } from '../../constants/routePaths';
import { formatDateLabel, formatDuration, formatTimeRange, minutesBetween } from '../../utils/date';
import { formatCredit, formatWon } from '../../utils/price';
import HoldCountdown from '../../components/reservation/HoldCountdown';
import Button from '../../components/common/Button';
import ErrorMessage from '../../components/common/ErrorMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';

export default function PaymentPage() {
  const { reservationId } = useParams();
  const router = useRouter();
  // HOLD 직후 예약 화면이 붙여 준 spaceVersion. App Router 에는 라우트 state 가 없어 쿼리로 받습니다.
  const spaceVersionParam = useSearchParams().get('spaceVersion');
  const { member, refreshMember } = useAuth();
  const { key: idempotencyKey } = useIdempotencyKey();
  const [locallyExpired, setLocallyExpired] = useState(false);
  const handleExpire = useCallback(() => setLocallyExpired(true), []);

  const fetchReservation = useCallback(() => getMyReservation(reservationId), [reservationId]);
  const { data: reservation, loading, error, run: reload } = useAsync(fetchReservation, [fetchReservation]);

  const pay = useAction(async () => {
    // 예약 상세 응답에는 spaceVersion 이 없다. HOLD 직후 넘겨받은 값을 쓰고,
    // 새로고침 등으로 쿼리가 사라졌을 때만 현재 공간 version 으로 대체한다.
    const spaceVersion =
      spaceVersionParam !== null
        ? Number(spaceVersionParam)
        : (await getSpace(reservation.spaceId)).version;
    await payReservation(
      reservationId,
      { spaceVersion },
      idempotencyKey,
    );
    // 잔액 재조회 실패가 이미 성공한 결제를 실패처럼 보이게 만들면 안 된다.
    refreshMember().catch(() => {});
    router.replace(ROUTES.reservationDetail(reservationId));
  });

  // 이미 결제됐거나 만료된 예약이면 상세로 돌려보냅니다(react-router 의 <Navigate/> 대체).
  const notHeld = Boolean(reservation) && reservation.status !== RESERVATION_STATUS.HELD;
  useEffect(() => {
    if (notHeld) router.replace(ROUTES.reservationDetail(reservationId));
  }, [notHeld, reservationId, router]);

  const slotCount = useMemo(() => {
    if (!reservation) return 0;
    return reservation.slotCount ?? minutesBetween(reservation.startTime, reservation.endTime) / 30;
  }, [reservation]);

  if (loading) return <LoadingSpinner label="결제 정보를 불러오는 중입니다…" />;
  if (error) return <ErrorMessage error={error} onRetry={reload} />;
  if (!reservation) return null;
  if (notHeld) return <LoadingSpinner label="예약 상세로 이동합니다…" />;

  const insufficient =
    typeof member?.balance === 'number' && member.balance < reservation.totalAmount;
  const paymentDisabled = locallyExpired || insufficient;
  const duration = minutesBetween(reservation.startTime, reservation.endTime);

  return (
    <div className="payment-page">
      <ol className="payment-steps" aria-label="예약 진행 단계">
        <li className="done"><span>✓</span><b>예약 정보</b></li>
        <li className="active" aria-current="step"><span>2</span><b>결제하기</b></li>
        <li><span>3</span><b>예약 완료</b></li>
      </ol>

      <div className="payment-heading">
        <h1>결제하기</h1>
        <p>선택한 예약 정보를 확인하고 크레딧 결제를 진행해 주세요.</p>
      </div>

      <div className="payment-layout">
        <div className="payment-main">
          <section className="payment-card">
            <h2>예약 정보</h2>
            <div className="payment-reservation">
              {reservation.spaceImagePath ? (
                <img src={reservation.spaceImagePath} alt="" />
              ) : (
                <div className="payment-image-placeholder" aria-hidden="true">Slot Key</div>
              )}
              <div>
                <div className="row wrap payment-space-title">
                  <h3>{reservation.spaceName}</h3>
                  <span className="badge cyan">{reservation.spaceType ?? '회의실'}</span>
                </div>
                <p>{reservation.spaceLocation}</p>
                <dl className="payment-booking-data">
                  <div><dt>이용 날짜</dt><dd>{formatDateLabel(reservation.date)}</dd></div>
                  <div><dt>이용 시간</dt><dd>{formatTimeRange(reservation.startTime, reservation.endTime)} ({formatDuration(duration)})</dd></div>
                  {reservation.partySize && <div><dt>이용 인원</dt><dd>{reservation.partySize}명</dd></div>}
                </dl>
              </div>
            </div>
          </section>

          <section>
            <div className="payment-section-title">
              <h2>결제 수단</h2>
              <p>원하는 결제 수단을 선택해 주세요.</p>
            </div>
            <label className="payment-method selected">
              <input type="radio" name="paymentMethod" checked readOnly />
              <span className="payment-method-icon" aria-hidden="true">▣</span>
              <span><strong>모의 결제</strong><small>보유 크레딧에서 즉시 차감됩니다.</small></span>
              <b>{formatCredit(member?.balance)}</b>
            </label>
          </section>

          <section className="payment-info-card">
            <span className="payment-info-icon" aria-hidden="true">i</span>
            <div>
              <h3>모의 결제 안내</h3>
              <p>실제 카드 결제가 아닌 프로토타입용 결제입니다. 결제하기를 누르면 예약 금액만큼 크레딧이 차감되고 예약이 확정됩니다.</p>
            </div>
          </section>

          <section className="payment-expiry-card">
            <span className="payment-clock" aria-hidden="true">◷</span>
            <div><h3>결제 만료 시간</h3><p>서버가 발급한 HOLD 만료 시각 전까지 결제를 완료해 주세요.</p></div>
            <HoldCountdown holdExpiresAt={reservation.holdExpiresAt} onExpire={handleExpire} compact />
          </section>
        </div>

        <aside className="payment-summary payment-card sticky">
          <div className="payment-summary-head">
            <h2>결제 금액</h2>
            <HoldCountdown holdExpiresAt={reservation.holdExpiresAt} onExpire={handleExpire} compact />
          </div>
          <div className="payment-summary-row">
            <span>기본 요금</span>
            <span>{formatWon(reservation.pricePerSlotSnapshot)} × {slotCount}슬롯</span>
            <strong>{formatWon(reservation.totalAmount)}</strong>
          </div>
          <div className="payment-summary-row">
            <span>할인 금액</span><span /><strong>-</strong>
          </div>
          <div className="payment-summary-total">
            <span>총 결제 금액</span><strong>{formatWon(reservation.totalAmount)}</strong>
          </div>

          <div className="payment-hold-notice">
            <span aria-hidden="true">i</span>
            <p>선택하신 시간은 결제 대기 상태로 임시 확보되어 있습니다. 만료 전에 결제를 완료해 주세요.</p>
          </div>

          <div className="payment-refund-guide" id="refund-policy">
            <div className="between row"><h3>취소 및 환불 안내</h3><a href="#refund-policy">자세히 보기 ›</a></div>
            <p><span className="refund-dot green">✓</span><strong>전액 환불</strong><small>이용 시작 1시간 전까지</small></p>
            <p><span className="refund-dot cyan">◷</span><strong>50% 환불</strong><small>이용 시작 1시간 전부터 시작 전까지</small></p>
            <p><span className="refund-dot red">×</span><strong>취소 불가</strong><small>이용 시작 이후</small></p>
          </div>

          <ErrorMessage error={pay.error} />
          {insufficient && <div className="form-error" role="alert">크레딧 잔액이 부족합니다.</div>}
          {locallyExpired && <div className="form-error" role="alert">결제 대기 시간이 끝났습니다. 새로 예약해 주세요.</div>}
          <Button variant="primary" wide loading={pay.loading} disabled={paymentDisabled} onClick={() => pay.execute().catch(() => {})}>
            🔒 {formatWon(reservation.totalAmount)} 결제하기
          </Button>
        </aside>
      </div>
    </div>
  );
}
