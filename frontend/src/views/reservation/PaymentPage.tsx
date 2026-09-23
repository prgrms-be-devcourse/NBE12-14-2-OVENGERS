'use client';

import { hasAllBookingAgreements } from '../../constants/bookingTerms';
import TermsAgreement from '../../components/reservation/TermsAgreement';
import ReservationSteps from '../../components/reservation/ReservationSteps';
import { markPaymentCompleted } from '../../utils/paymentCelebration';
import SpacePhoto from '../../components/space/SpacePhoto';
import { useCallback, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { getMyReservation, payReservation } from '../../api/reservationApi';
import { getSpace } from '../../api/spaceApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { useAuth } from '../../hooks/useAuth';
import { useIdempotencyKey } from '../../hooks/useIdempotencyKey';
import { RESERVATION_STATUS } from '../../constants/enums';
import { ROUTES } from '../../constants/routePaths';
import {
  formatDateLabel,
  formatDuration,
  formatTimeRange,
  minutesBetween,
} from '../../utils/date';
import { formatCredit, formatWon } from '../../utils/price';
import HoldCountdown from '../../components/reservation/HoldCountdown';
import Button from '../../components/common/Button';
import ErrorMessage from '../../components/common/ErrorMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import { useRouteId } from '@/hooks/useRouteId';

export default function PaymentPage() {
  const reservationId = useRouteId('reservationId');
  const router = useRouter();

  // HOLD 직후 예약 화면이 붙여 준 spaceVersion.
  const spaceVersionParam = useSearchParams().get('spaceVersion');

  const { member, refreshMember } = useAuth();
  const { key: idempotencyKey } = useIdempotencyKey();

  const [agreementState, setAgreementState] = useState<{ reservationId: string; ids: string[] }>({ reservationId: '', ids: [] });
  const acceptedIds = agreementState.reservationId === String(reservationId) ? agreementState.ids : [];
  const agreed = hasAllBookingAgreements(acceptedIds);
  const [locallyExpired, setLocallyExpired] = useState(false);

  const handleExpire = useCallback(
      () => setLocallyExpired(true),
      [],
  );

  const fetchReservation = useCallback(
      () => getMyReservation(reservationId),
      [reservationId],
  );

  const {
    data: reservation,
    loading,
    error,
    run: reload,
  } = useAsync(fetchReservation, [fetchReservation]);

  const pay = useAction(async () => {
    if (!reservation || !agreed || locallyExpired) return;

    const spaceVersion =
        spaceVersionParam !== null
            ? Number(spaceVersionParam)
            : (await getSpace(reservation.spaceId)).version;

    await payReservation(
        reservation.reservationId,
        { spaceVersion },
        idempotencyKey,
    );

    markPaymentCompleted(reservationId);

    // 결제 성공 후 회원 크레딧 잔액 갱신
    refreshMember().catch(() => {});

    router.replace(
        ROUTES.reservationDetail(reservationId),
    );
  });

  // 이미 결제됐거나 만료된 예약이면 상세 화면으로 이동
  const notHeld =
      reservation !== null &&
      reservation.status !== RESERVATION_STATUS.HELD;

  useEffect(() => {
    if (notHeld) {
      router.replace(
          ROUTES.reservationDetail(reservationId),
      );
    }
  }, [notHeld, reservationId, router]);

  if (loading) {
    return (
        <LoadingSpinner label="결제 정보를 불러오는 중입니다…" />
    );
  }

  if (error) {
    return (
        <ErrorMessage
            error={error}
            onRetry={reload}
        />
    );
  }

  if (!reservation) return null;

  if (notHeld) {
    return (
        <LoadingSpinner label="예약 상세로 이동합니다…" />
    );
  }

  /*
   * 백엔드에서 startTime / endTime이
   * 2026-09-18T18:30:00 형식으로 내려오기 때문에
   * 날짜와 시간 부분을 각각 잘라서 사용한다.
   */
  const date = reservation.startTime.slice(0, 10);

  const startTime =
      reservation.startTime.slice(11, 16);

  const endTime =
      reservation.endTime.slice(11, 16);

  const duration =
      minutesBetween(startTime, endTime);

  const slotCount =
      duration / 30;

  const insufficient =
      typeof member?.balance === 'number' &&
      member.balance < reservation.totalAmount;

  const paymentDisabled =
      locallyExpired || insufficient || !agreed;

  return (
      <div className="payment-page">
        <ReservationSteps currentStep={2} />

        <div className="payment-heading">
          <p className="page-kicker">
            CONFIRM YOUR BOOKING
          </p>

          <h1>예약을 마무리하세요</h1>

          <p>
            선택한 예약 정보를 확인하고
            크레딧 결제를 진행해 주세요.
          </p>
        </div>

        <div className="payment-layout">
          <div className="payment-main">
            <section className="payment-card">
              <h2>예약 정보</h2>

              <div className="payment-reservation">
                <SpacePhoto
                    src={reservation.spaceImagePath}
                    alt={reservation.spaceName}
                />

                <div>
                  <div className="row wrap payment-space-title">
                    <h3>
                      {reservation.spaceName}
                    </h3>

                    {reservation.spaceType && (
                        <span className="badge cyan">
                      {reservation.spaceType}
                    </span>
                    )}
                  </div>

                  <p>
                    {reservation.spaceLocation}
                  </p>

                  <dl className="payment-booking-data">
                    <div>
                      <dt>이용 날짜</dt>
                      <dd>
                        {formatDateLabel(date)}
                      </dd>
                    </div>

                    <div>
                      <dt>이용 시간</dt>
                      <dd>
                        {formatTimeRange(
                            startTime,
                            endTime,
                        )}{' '}
                        ({formatDuration(duration)})
                      </dd>
                    </div>

                    {reservation.partySize && (
                        <div>
                          <dt>이용 인원</dt>
                          <dd>
                            {reservation.partySize}명
                          </dd>
                        </div>
                    )}
                  </dl>
                </div>
              </div>
            </section>

            <section>
              <div className="payment-section-title">
                <h2>결제 수단</h2>
                <p>
                  보유 크레딧으로 결제합니다.
                </p>
              </div>

              <label className="payment-method selected">
                <input
                    type="radio"
                    name="paymentMethod"
                    checked
                    readOnly
                />

                <span
                    className="payment-method-icon"
                    aria-hidden="true"
                >
                ▣
              </span>

                <span>
                <strong>모의 결제</strong>
                <small>
                  보유 크레딧에서 즉시 차감됩니다.
                </small>
              </span>

                <b>
                  {formatCredit(member?.balance)}
                </b>
              </label>
            </section>

            <section className="payment-info-card">
            <span
                className="payment-info-icon"
                aria-hidden="true"
            >
              i
            </span>

              <div>
                <h3>모의 결제 안내</h3>
                <p>
                  실제 금액이 청구되지 않는
                  모의 결제입니다. 결제하면
                  크레딧이 차감되고 예약이
                  확정됩니다.
                </p>
              </div>
            </section>

            <section className="payment-expiry-card">
            <span
                className="payment-clock"
                aria-hidden="true"
            >
              ◷
            </span>

              <div>
                <h3>결제 만료 시간</h3>
                <p>
                  남은 시간 안에 결제를
                  완료해 주세요.
                </p>
              </div>

              {reservation.holdExpiresAt && (
                  <HoldCountdown
                      holdExpiresAt={
                        reservation.holdExpiresAt
                      }
                      onExpire={handleExpire}
                      compact
                  />
              )}
            </section>
          </div>

          <aside className="payment-summary payment-card sticky">
            <div className="payment-summary-head">
              <h2>결제 금액</h2>

              {reservation.holdExpiresAt && (
                  <HoldCountdown
                      holdExpiresAt={
                        reservation.holdExpiresAt
                      }
                      onExpire={handleExpire}
                      compact
                  />
              )}
            </div>

            <div className="payment-summary-row">
              <span>기본 요금</span>

              <span>
              {formatWon(
                  reservation.pricePerSlotSnapshot,
              )}{' '}
                × {slotCount}슬롯
            </span>

              <strong>
                {formatWon(
                    reservation.totalAmount,
                )}
              </strong>
            </div>

            <div className="payment-summary-row">
              <span>할인 금액</span>
              <span />
              <strong>-</strong>
            </div>

            <div className="payment-summary-total">
              <span>총 결제 금액</span>

              <strong>
                {formatWon(
                    reservation.totalAmount,
                )}
              </strong>
            </div>

            <div className="payment-hold-notice">
            <span aria-hidden="true">
              i
            </span>

              <p>
                선택하신 시간은 결제 대기
                상태로 임시 확보되어 있습니다.
                만료 전에 결제를 완료해 주세요.
              </p>
            </div>

            <div
                className="payment-refund-guide"
                id="refund-policy"
            >
              <div className="between row">
                <h3>취소 및 환불 안내</h3>
              </div>

              <p>
              <span className="refund-dot green">
                ✓
              </span>

                <strong>전액 환불</strong>

                <small>
                  이용 시작 1시간 전까지
                </small>
              </p>

              <p>
              <span className="refund-dot cyan">
                ◷
              </span>

                <strong>50% 환불</strong>

                <small>
                  이용 시작 1시간 전부터
                  시작 전까지
                </small>
              </p>

              <p>
              <span className="refund-dot red">
                ×
              </span>

                <strong>취소 불가</strong>

                <small>
                  이용 시작 이후
                </small>
              </p>
            </div>

            <TermsAgreement
              key={reservationId}
              acceptedIds={acceptedIds}
              onChange={(ids) => setAgreementState({ reservationId: String(reservationId), ids })}
              disabled={pay.loading || locallyExpired}
            />
            <ErrorMessage error={pay.error} />

            {insufficient && (
                <div
                    className="form-error"
                    role="alert"
                >
                  크레딧 잔액이 부족합니다.
                </div>
            )}

            {locallyExpired && (
                <div
                    className="form-error"
                    role="alert"
                >
                  결제 대기 시간이 끝났습니다.
                  새로 예약해 주세요.
                </div>
            )}

            <Button
                variant="primary"
                wide
                loading={pay.loading}
                disabled={paymentDisabled}
                onClick={() =>
                    pay.execute().catch(() => {})
                }
            >
              {formatWon(
                  reservation.totalAmount,
              )}{' '}
              크레딧 결제하기
            </Button>
          </aside>
        </div>
      </div>
  );
}