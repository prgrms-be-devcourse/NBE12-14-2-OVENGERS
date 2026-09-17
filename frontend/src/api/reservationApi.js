import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';

/**
 * 예약 생성 = 슬롯 확보(HOLD). 결제는 아직 일어나지 않으므로 Idempotency-Key가 필요 없다
 * (core-domain-decisions.md 2-1, api-spec.md 5-1).
 * 응답에 totalAmount, space.version, holdExpiresAt 이 포함된다.
 */
export function createReservation({ spaceId, date, startTime, endTime, termsVersion }) {
  return api.post(API_ROUTES.reservations.create, {
    spaceId,
    date,
    startTime,
    endTime,
    termsVersion,
  });
}

/**
 * 결제 확인 및 확정. 돈이 움직이는 지점이므로 Idempotency-Key 헤더가 필수다.
 * spaceVersion 은 HOLD 생성 응답에서 받은 값을 그대로 되돌려 보낸다 —
 * 값이 다르면(가격 변경) 서버가 SPACE_VERSION_MISMATCH(409)로 거절한다(core-domain-decisions.md 5-2).
 */
export function payReservation(reservationId, { spaceVersion }, idempotencyKey) {
  return api.post(
    API_ROUTES.reservations.pay(reservationId),
    { spaceVersion },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  );
}

export function getMyReservations({ page = 0, size = 10, status } = {}) {
  return api.get(API_ROUTES.reservations.list, { query: { page, size, status } });
}

export function getMyReservation(reservationId) {
  return api.get(API_ROUTES.reservations.detail(reservationId));
}

/**
 * 예약자 본인만 취소할 수 있다. 시작 1시간 전까지 100%, 1시간 전~시작 전 50% 환불,
 * 시작 이후에는 취소할 수 없다(체크아웃으로만 종료). core-domain-decisions.md 6-1.
 */
export function cancelReservation(reservationId) {
  return api.post(API_ROUTES.reservations.cancel(reservationId));
}

/**
 * 연장. 기존 슬롯은 그대로 두고 추가 슬롯만 확보하므로 실패해도 원 예약은 무손상이다.
 * expectedEndTime 은 화면이 마지막으로 읽은 reservation.endTime — 서버가 이 값으로
 * 낙관적 검사(WHERE end_time=:expectedEndTime)를 하여 중복 연장 요청을 걸러낸다(core-domain-decisions.md 7-2).
 */
export function extendReservation(reservationId, { newEndTime, expectedEndTime }) {
  return api.post(API_ROUTES.reservations.extend(reservationId), {
    newEndTime,
    expectedEndTime,
  });
}

/**
 * 체크아웃. IN_USE 상태에서만 가능하며 되돌릴 수 없다(core-domain-decisions.md 8-4).
 * 슬롯 반환·환불 없음 — 화면에서 확인 다이얼로그를 거치도록 한다.
 */
export function checkOutReservation(reservationId) {
  return api.post(API_ROUTES.reservations.checkOut(reservationId));
}
