/**
 * 서버 Enum 값과 화면 표기를 함께 정의합니다.
 * 값 목록의 기준은 docs/core-domain-decisions.md(확정본)와 docs/api-spec.md 입니다.
 *
 * tone 은 global.css 의 badge 클래스(green / red / gray / cyan)에 대응합니다.
 */

import type {
  AccessReasonCode,
  AccessResult,
  Meta,
  MemberRole,
  MemberStatus,
  ReservationStatus,
  SelectOption,
  SpaceStatus,
} from '../types/api';

export const MEMBER_ROLE: Record<MemberRole, MemberRole> = {
  USER: 'USER',
  ADMIN: 'ADMIN',
};

export const MEMBER_ROLE_LABEL: Record<MemberRole, string> = {
  USER: '일반 회원',
  ADMIN: '플랫폼 관리자',
};

export const MEMBER_STATUS: Record<MemberStatus, MemberStatus> = {
  ACTIVE: 'ACTIVE',
  SUSPENDED: 'SUSPENDED',
};

export const MEMBER_STATUS_META: Record<MemberStatus, Meta> = {
  ACTIVE: { label: '이용 중', tone: 'green' },
  SUSPENDED: { label: '정지', tone: 'red' },
};

export const SPACE_STATUS: Record<SpaceStatus, SpaceStatus> = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
};

export const SPACE_STATUS_META: Record<SpaceStatus, Meta> = {
  ACTIVE: { label: '예약 가능', tone: 'green' },
  INACTIVE: { label: '운영 중지', tone: 'gray' },
};

/**
 * 예약 상태 7종 (core-domain-decisions.md 3-2).
 * HELD -(결제 성공)-> CONFIRMED -(최초 체크인)-> IN_USE -(체크아웃/종료)-> COMPLETED
 *   ㄴ(10분 경과)-> EXPIRED   CONFIRMED -(취소)-> CANCELLED   CONFIRMED -(체크인 안 함)-> NO_SHOW
 */
export const RESERVATION_STATUS: Record<ReservationStatus, ReservationStatus> = {
  HELD: 'HELD',
  EXPIRED: 'EXPIRED',
  CONFIRMED: 'CONFIRMED',
  IN_USE: 'IN_USE',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  NO_SHOW: 'NO_SHOW',
};

export const RESERVATION_STATUS_META: Record<ReservationStatus, Meta> = {
  HELD: { label: '결제 대기', tone: 'cyan' },
  EXPIRED: { label: '만료됨', tone: 'gray' },
  CONFIRMED: { label: '확정', tone: 'green' },
  IN_USE: { label: '이용 중', tone: 'cyan' },
  COMPLETED: { label: '이용 완료', tone: 'gray' },
  CANCELLED: { label: '취소', tone: 'red' },
  NO_SHOW: { label: '노쇼', tone: 'red' },
};

export const ACCESS_RESULT: Record<AccessResult, AccessResult> = {
  ALLOW: 'ALLOW',
  DENY: 'DENY',
};

export const ACCESS_RESULT_META: Record<AccessResult, Meta> = {
  ALLOW: { label: '출입 허용', tone: 'green' },
  DENY: { label: '출입 거절', tone: 'red' },
};

/**
 * 출입 거절 사유 코드 → 사용자에게 보여줄 문구.
 * docs/api-spec.md 7장 door-access/verify 의 reasonCode 값 기준.
 */
const ACCESS_REASON_LABEL: Record<AccessReasonCode, string> = {
  ALLOWED: '출입이 허용되었습니다.',
  TOKEN_NOT_FOUND: '등록되지 않은 출입 키입니다.',
  TOKEN_REVOKED: '이미 무효화된 출입 키입니다.',
  MEMBER_MISMATCH: '본인의 예약에 발급된 출입 키가 아닙니다.',
  RESERVATION_NOT_ACTIVE: '취소·종료·노쇼 처리된 예약입니다.',
  OUTSIDE_ALLOWED_TIME: '출입할 수 있는 시간이 아닙니다.',
  SPACE_MISMATCH: '예약한 공간이 아닙니다.',
};

/**
 * 사유 코드를 문구로 바꿉니다.
 * 서버가 표에 없는 코드를 새로 내려도 화면이 비지 않도록 코드 자체를 돌려줍니다.
 */
export function accessReasonLabel(reasonCode: string): string {
  return ACCESS_REASON_LABEL[reasonCode as AccessReasonCode] ?? reasonCode;
}

/** 목록 화면의 상태 필터 옵션 */
export const RESERVATION_STATUS_OPTIONS: SelectOption[] = [
  { value: '', label: '전체 상태' },
  { value: RESERVATION_STATUS.HELD, label: '결제 대기' },
  { value: RESERVATION_STATUS.CONFIRMED, label: '확정' },
  { value: RESERVATION_STATUS.IN_USE, label: '이용 중' },
  { value: RESERVATION_STATUS.COMPLETED, label: '이용 완료' },
  { value: RESERVATION_STATUS.CANCELLED, label: '취소' },
  { value: RESERVATION_STATUS.NO_SHOW, label: '노쇼' },
  { value: RESERVATION_STATUS.EXPIRED, label: '만료됨' },
];

export const SPACE_STATUS_OPTIONS: SelectOption[] = [
  { value: '', label: '전체 상태' },
  { value: SPACE_STATUS.ACTIVE, label: '예약 가능' },
  { value: SPACE_STATUS.INACTIVE, label: '운영 중지' },
];

export const MEMBER_STATUS_OPTIONS: SelectOption[] = [
  { value: '', label: '전체 상태' },
  { value: MEMBER_STATUS.ACTIVE, label: '이용 중' },
  { value: MEMBER_STATUS.SUSPENDED, label: '정지' },
];

/** 예약 시 동의하는 약관 버전. 서버가 예약에 함께 저장합니다. */
export const TERMS_VERSION = 'v1.1';

/** 예약(HOLD)이 결제 대기 상태로 유지되는 시간(분). core-domain-decisions.md 2-1. */
export const HOLD_DURATION_MINUTES = 10;

/** 최초 체크인 마감 = 시작 시각 + 15분(core-domain-decisions.md 6-3, 8-1). NO_SHOW 판정과 동일한 값. */
export const CHECK_IN_DEADLINE_MINUTES = 15;
