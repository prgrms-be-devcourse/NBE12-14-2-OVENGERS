/**
 * 서버 응답·요청 타입.
 *
 * 기준 문서: docs/api-spec.md, docs/core-domain-decisions.md.
 * 서버 DTO 가 바뀌면 이 파일을 먼저 고치고, 컴파일 오류가 가리키는 화면을 따라갑니다.
 */

/* ------------------------------------------------------------------ 공통 */

/** 서버 페이지네이션 응답 */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** global.css 의 badge 클래스와 1:1 대응 */
export type Tone = 'default' | 'green' | 'red' | 'gray' | 'cyan';

export interface Meta {
  label: string;
  tone: Tone;
}

export interface SelectOption {
  value: string | number;
  label: string;
}

/* ------------------------------------------------------------------ 회원 */

export type MemberRole = 'USER' | 'ADMIN';
export type MemberStatus = 'ACTIVE' | 'SUSPENDED';

export interface Member {
  memberId: number;
  email: string;
  nickname: string;
  role: MemberRole;
  status: MemberStatus;
  /** 크레딧 잔액. 1크레딧 = 1원 (core-domain-decisions.md 1-2) */
  balance: number;
  createdAt: string;
}

export interface AdminMember extends Member {
  lastStatusChangedAt: string | null;
  lastStatusChangeReason: string | null;
}

export interface AuthTokens {
  accessToken: string;
}

/** 로그인 응답. member 를 함께 내려주지 않으면 화면이 /members/me 로 한 번 더 조회합니다. */
export interface LoginResponse extends AuthTokens {
  member?: Member;
}

export interface SignupRequest {
  email: string;
  password: string;
  passwordConfirm: string;
  nickname: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/* ------------------------------------------------------------------ 공간 */

export type SpaceStatus = 'ACTIVE' | 'INACTIVE';

export interface Space {
  id: number;
  name: string;
  location: string;
  description: string | null;
  capacity: number;
  /** 30분당 정액 요금(100원 단위). 시간당 요금 표현은 쓰지 않습니다. */
  pricePerSlot: number;
  imagePath: string | null;
  openingTime: string;
  closingTime: string;
  status: SpaceStatus;
  /** 낙관적 잠금용. HOLD 응답의 spaceVersion 과 비교합니다. */
  version: number;
  /** 목록 조회에서 date 를 넘겼을 때만 내려옵니다. */
  availability?: SlotAvailability[];
  updatedAt?: string;
  updatedByNickname?: string;
}

/** 공간 등록·수정 폼의 값. 숫자 입력은 제출 직전에 Number 로 정규화합니다. */
export interface SpaceFormValues {
  name: string;
  location: string;
  description: string | null;
  capacity: number | string;
  pricePerSlot: number | string;
  imagePath: string | null;
  openingTime: string;
  closingTime: string;
  status: SpaceStatus;
}

/* ------------------------------------------------------------------ 슬롯 */

export interface SlotAvailability {
  startTime: string;
  endTime?: string;
  available: boolean;
}

/** 서버 가용성 + 화면 판단(지난 시간 여부)을 합친 값 */
export interface DecoratedSlot extends SlotAvailability {
  past: boolean;
  selectable: boolean;
  reason: string | null;
}

export interface SpaceSlotsResponse {
  date: string;
  slots: SlotAvailability[];
}

/* ------------------------------------------------------------------ 예약 */

/** 예약 상태 7종 (core-domain-decisions.md 3-2) */
export type ReservationStatus =
  | 'HELD'
  | 'EXPIRED'
  | 'CONFIRMED'
  | 'IN_USE'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW';

export interface StatusHistory {
  id: number;
  fromStatus: ReservationStatus | null;
  toStatus: ReservationStatus;
  /** 없으면 스케줄러가 처리한 것입니다. */
  actorNickname: string | null;
  reason: string | null;
  createdAt: string;
}

export interface ReservationSummary {
  reservationId: number;
  spaceId: number;
  spaceName: string;
  spaceLocation?: string;
  spaceImagePath: string | null;
  date: string;
  startTime: string;
  endTime: string;
  status: ReservationStatus;
  totalAmount: number;
}

export interface Reservation extends ReservationSummary {
  spaceType?: string | null;
  partySize?: number | null;
  slotCount?: number;
  /** 확정 당시 요금. 이후 공간 요금이 바뀌어도 이 값은 유지됩니다. */
  pricePerSlotSnapshot: number;
  termsVersion: string;
  /** HELD 상태에서만 의미가 있습니다. */
  holdExpiresAt: string | null;
  checkedInAt: string | null;
  checkedOutAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
  statusHistories: StatusHistory[];
  accessKey: { active: boolean; issuedAt: string | null } | null;
}

export interface AdminReservation extends Reservation {
  memberNickname: string;
  memberEmail: string;
  accessLogs: DoorAccessLog[];
}

/** POST /reservations 의 응답. spaceVersion 은 이 응답에만 있습니다(api-spec.md 5-1). */
export interface HoldReservation {
  reservationId: number;
  spaceVersion: number;
  totalAmount: number;
  holdExpiresAt: string;
}

export interface CreateReservationRequest {
  spaceId: number;
  date: string;
  startTime: string;
  endTime: string;
  termsVersion: string;
}

/* ------------------------------------------------------------------ 출입 */

export type AccessResult = 'ALLOW' | 'DENY';

export type AccessReasonCode =
    | 'ALLOWED'
    | 'TOKEN_NOT_FOUND'
    | 'TOKEN_REVOKED'
    | 'RESERVATION_NOT_ACTIVE'
    | 'OUTSIDE_ALLOWED_TIME'
    | 'SPACE_MISMATCH'
    | 'MEMBER_MISMATCH';

/** 토큰 원문은 발급 응답에만 있습니다. 서버에는 해시만 저장되어 재조회할 수 없습니다. */
export interface IssuedDoorToken {
  reservationId: number;
  token: string;
  issuedAt: string;
}

export interface AccessVerifyResult {
  result: AccessResult;
  reasonCode: AccessReasonCode | null;
  spaceName: string | null;
  firstCheckIn: boolean | null;
  attemptedAt: string;
}

export interface DoorAccessLog {
  accessLogId: number;
  attemptedAt: string;
  requestedSpaceName: string | null;
  result: AccessResult;
  reasonCode: AccessReasonCode | string | null;
}
