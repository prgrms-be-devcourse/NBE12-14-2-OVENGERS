'use client';

import { useCallback, useMemo, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { getSpace, getSpaceSlots } from '../../api/spaceApi';
import { createReservation } from '../../api/reservationApi';
import { useAsync, useAction } from '../../hooks/useApi';
import { useAuth } from '../../hooks/useAuth';
import { useSlotSelection } from '../../hooks/useSlotSelection';
import { ROUTES } from '../../constants/routePaths';
import { SPACE_STATUS, SPACE_STATUS_META, TERMS_VERSION } from '../../constants/enums';
import { ERROR_CODE } from '../../constants/errorCodes';
import { today } from '../../utils/date';
import { decorateSlots } from '../../utils/slot';
import { calculateTotal, formatPricePerSlot } from '../../utils/price';
import { MetaBadge } from '../../components/common/Badge';
import SlotPicker from '../../components/space/SlotPicker';
import PriceSummary from '../../components/reservation/PriceSummary';
import TermsAgreement from '../../components/reservation/TermsAgreement';
import Button from '../../components/common/Button';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';

export default function SpaceDetailPage() {
  const { spaceId } = useParams();
  const router = useRouter();
  const { isAuthenticated } = useAuth();

  const [date, setDate] = useState(today());
  const [agreed, setAgreed] = useState(false);

  const fetchSpace = useCallback(() => getSpace(spaceId), [spaceId]);
  const { data: space, loading: spaceLoading, error: spaceError } = useAsync(fetchSpace, [fetchSpace]);

  const fetchSlots = useCallback(() => getSpaceSlots(spaceId, date), [spaceId, date]);
  const { data: slotData, loading: slotLoading, run: reloadSlots } = useAsync(fetchSlots, [fetchSlots]);

  const slots = useMemo(
    () => decorateSlots(slotData?.slots ?? [], date),
    [slotData, date],
  );
  const selection = useSlotSelection(slots);

  const clientAmount = space ? calculateTotal(space.pricePerSlot, selection.slotCount) : 0;

  /**
   * 이 요청은 슬롯 확보(HOLD)일 뿐 결제가 아니다 — 그래서 Idempotency-Key가 필요 없다
   * (core-domain-decisions.md 2-1). 결제 확인은 별도 결제 화면에서 이루어진다.
   */
  const { execute: submit, loading: submitting, error: submitError, setError } = useAction(
    async () => {
      const reservation = await createReservation({
        spaceId: Number(spaceId),
        date,
        startTime: selection.startTime,
        endTime: selection.endTime,
        termsVersion: TERMS_VERSION,
      });
      // spaceVersion 은 HOLD 응답에만 있으므로 결제 화면으로 넘겨준다(api-spec.md 5-1).
      // App Router 에는 라우트 state 가 없어 쿼리스트링으로 전달한다.
      router.push(
        `${ROUTES.reservationPayment(reservation.reservationId)}?spaceVersion=${reservation.spaceVersion}`,
      );
    },
  );

  const handleSubmit = async () => {
    try {
      await submit();
    } catch (error) {
      // 슬롯 충돌은 다른 사용자가 먼저 예약한 경우이므로 현황을 새로 불러옵니다.
      if (error?.code === ERROR_CODE.RESERVATION_SLOT_CONFLICT) {
        selection.clear();
        reloadSlots().catch(() => {});
      }
    }
  };

  if (spaceLoading) return <LoadingSpinner />;
  if (spaceError) return <ErrorMessage error={spaceError} />;
  if (!space) return null;

  const inactive = space.status !== SPACE_STATUS.ACTIVE;
  const disabledReason = !isAuthenticated
    ? '예약하려면 로그인이 필요합니다.'
    : inactive
      ? '현재 신규 예약을 받지 않는 공간입니다.'
      : !selection.hasSelection
        ? '이용할 시간을 선택해 주세요.'
        : !agreed
          ? '이용 약관에 동의해 주세요.'
          : null;

  return (
    <>
      <nav className="crumb" aria-label="현재 위치">
        <Link href={ROUTES.spaces}>공간 찾기</Link>
        <span>›</span>
        <span>{space.name}</span>
      </nav>

      <div className="split">
        <div>
          {space.imagePath && <img className="detailphoto" src={space.imagePath} alt="" />}

          <div className="pagehead">
            <div>
              <div className="row wrap">
                <MetaBadge meta={SPACE_STATUS_META[space.status]} />
              </div>
              <h1>{space.name}</h1>
              <div className="meta">
                <span>{space.location}</span>
                <span>최대 {space.capacity}명</span>
                <span>
                  운영 {space.openingTime} ~ {space.closingTime}
                </span>
                <span>{formatPricePerSlot(space.pricePerSlot)}</span>
              </div>
            </div>
          </div>

          {space.description && <p>{space.description}</p>}

          <div className="section">
            <h2>이용 시간 선택</h2>
            <label className="field" style={{ maxWidth: 260 }}>
              <span>날짜</span>
              <input
                type="date"
                min={today()}
                value={date}
                onChange={(event) => {
                  setDate(event.target.value);
                  selection.clear();
                }}
              />
            </label>

            {slotLoading ? (
              <LoadingSpinner label="예약 가능한 시간을 불러오는 중입니다…" />
            ) : (
              <SlotPicker
                slots={slots}
                isSelected={selection.isSelected}
                onSelect={(startTime) => {
                  setError(null);
                  selection.select(startTime);
                }}
                disabled={inactive}
              />
            )}
          </div>
        </div>

        <aside className="panel sticky">
          <h3>예약 내용</h3>
          <PriceSummary
            space={space}
            date={date}
            startTime={selection.startTime}
            endTime={selection.endTime}
            slotCount={selection.slotCount}
            clientAmount={clientAmount}
          />

          <div className="divider" />

          {isAuthenticated ? (
            <>
              <TermsAgreement checked={agreed} onChange={setAgreed} disabled={submitting} />
              <ErrorMessage error={submitError} />
              <Button
                variant="primary"
                wide
                loading={submitting}
                disabled={Boolean(disabledReason)}
                onClick={handleSubmit}
              >
                예약하기
              </Button>
              {disabledReason && <p className="form-help">{disabledReason}</p>}
            </>
          ) : (
            <>
              <p className="muted">예약하려면 로그인이 필요합니다.</p>
              <Link href={ROUTES.login} className="btn primary wide">
                로그인하고 예약하기
              </Link>
            </>
          )}

          <p className="space-note muted">
            화면에 예약 가능으로 보여도 확정은 서버가 판단합니다. 예약 후에는 결제 대기(HOLD)
            상태가 되며, 10분 안에 결제를 완료해야 확정됩니다.
          </p>
        </aside>
      </div>
    </>
  );
}
