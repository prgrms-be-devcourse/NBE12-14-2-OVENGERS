/**
 * 서버 오류 코드 → 사용자 문구.
 * 코드 목록의 기준은 docs/api-spec.md 의 "공통 오류 코드" 및 각 API 섹션입니다.
 *
 * 서버 message 를 그대로 노출하지 않고 여기서 다시 쓰는 이유:
 * 서버 문구는 개발자용으로 바뀔 수 있고, 화면마다 필요한 안내가 다르기 때문입니다.
 */

export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'INVALID_CREDENTIALS'
  | 'INVALID_REFRESH_TOKEN'
  | 'INVALID_ACCESS_TOKEN'
  | 'AUTHENTICATION_REQUIRED'
  | 'ACCOUNT_INACTIVE'
  | 'ACCESS_DENIED'
  | 'UNAUTHENTICATED'
  | 'MEMBER_SUSPENDED'
  | 'MEMBER_NOT_ACTIVE'
  | 'FORBIDDEN_ROLE'
  | 'FORBIDDEN_NOT_OWNER'
  | 'MEMBER_NOT_FOUND'
  | 'SPACE_NOT_FOUND'
  | 'RESERVATION_NOT_FOUND'
  | 'EMAIL_ALREADY_EXISTS'
  | 'RESERVATION_SLOT_CONFLICT'
  | 'RESERVATION_STATE_CONFLICT'
  | 'IDEMPOTENCY_KEY_CONFLICT'
  | 'SPACE_INACTIVE'
  | 'SPACE_VERSION_MISMATCH'
  | 'SPACE_OPERATING_HOURS_CONFLICT'
  | 'INSUFFICIENT_BALANCE'
  | 'SELF_GRANT_NOT_ALLOWED'
  | 'TARGET_IS_ADMIN'
  | 'RESERVATION_NOT_CONFIRMED'
  | 'INQUIRY_NOT_FOUND'
  | 'INQUIRY_ALREADY_ANSWERED'
  | 'INVALID_TIME_RANGE'
  | 'NETWORK_ERROR';

export const ERROR_CODE: Record<ErrorCode, ErrorCode> = {
  VALIDATION_FAILED: 'VALIDATION_FAILED',
  INVALID_CREDENTIALS: 'INVALID_CREDENTIALS',
  INVALID_REFRESH_TOKEN: 'INVALID_REFRESH_TOKEN',
  INVALID_ACCESS_TOKEN: 'INVALID_ACCESS_TOKEN',
  AUTHENTICATION_REQUIRED: 'AUTHENTICATION_REQUIRED',
  ACCOUNT_INACTIVE: 'ACCOUNT_INACTIVE',
  ACCESS_DENIED: 'ACCESS_DENIED',
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  MEMBER_SUSPENDED: 'MEMBER_SUSPENDED',
  MEMBER_NOT_ACTIVE: 'MEMBER_NOT_ACTIVE',
  FORBIDDEN_ROLE: 'FORBIDDEN_ROLE',
  FORBIDDEN_NOT_OWNER: 'FORBIDDEN_NOT_OWNER',
  MEMBER_NOT_FOUND: 'MEMBER_NOT_FOUND',
  SPACE_NOT_FOUND: 'SPACE_NOT_FOUND',
  RESERVATION_NOT_FOUND: 'RESERVATION_NOT_FOUND',
  EMAIL_ALREADY_EXISTS: 'EMAIL_ALREADY_EXISTS',
  RESERVATION_SLOT_CONFLICT: 'RESERVATION_SLOT_CONFLICT',
  RESERVATION_STATE_CONFLICT: 'RESERVATION_STATE_CONFLICT',
  IDEMPOTENCY_KEY_CONFLICT: 'IDEMPOTENCY_KEY_CONFLICT',
  SPACE_INACTIVE: 'SPACE_INACTIVE',
  SPACE_VERSION_MISMATCH: 'SPACE_VERSION_MISMATCH',
  SPACE_OPERATING_HOURS_CONFLICT: 'SPACE_OPERATING_HOURS_CONFLICT',
  INSUFFICIENT_BALANCE: 'INSUFFICIENT_BALANCE',
  SELF_GRANT_NOT_ALLOWED: 'SELF_GRANT_NOT_ALLOWED',
  TARGET_IS_ADMIN: 'TARGET_IS_ADMIN',
  RESERVATION_NOT_CONFIRMED: 'RESERVATION_NOT_CONFIRMED',
  INQUIRY_NOT_FOUND: 'INQUIRY_NOT_FOUND',
  INQUIRY_ALREADY_ANSWERED: 'INQUIRY_ALREADY_ANSWERED',
  INVALID_TIME_RANGE: 'INVALID_TIME_RANGE',
  NETWORK_ERROR: 'NETWORK_ERROR',
};

const MESSAGES: Record<ErrorCode, string> = {
  INQUIRY_NOT_FOUND: '문의를 찾을 수 없습니다. 목록에서 다시 확인해 주세요.',
  INQUIRY_ALREADY_ANSWERED: '이미 답변이 등록된 문의입니다. 최신 내용을 확인해 주세요.',
  INVALID_TIME_RANGE: '종료 시간은 시작 시간보다 늦어야 합니다.',
  VALIDATION_FAILED: '입력한 내용을 다시 확인해 주세요.',
  INVALID_CREDENTIALS: '이메일 또는 비밀번호가 올바르지 않습니다.',
  INVALID_REFRESH_TOKEN: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  INVALID_ACCESS_TOKEN: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  AUTHENTICATION_REQUIRED: '로그인이 필요합니다.',
  ACCOUNT_INACTIVE: '이용이 제한된 계정입니다. 관리자에게 문의해 주세요.',
  ACCESS_DENIED: '접근 권한이 없습니다.',
  UNAUTHENTICATED: '로그인이 필요합니다.',
  MEMBER_SUSPENDED: '정지된 계정입니다. 관리자에게 문의해 주세요.',
  MEMBER_NOT_ACTIVE: '정지된 계정은 이 기능을 사용할 수 없습니다.',
  FORBIDDEN_ROLE: '접근 권한이 없습니다.',
  FORBIDDEN_NOT_OWNER: '예약자 본인만 사용할 수 있습니다.',
  MEMBER_NOT_FOUND: '회원을 찾을 수 없습니다.',
  SPACE_NOT_FOUND: '공간을 찾을 수 없습니다.',
  RESERVATION_NOT_FOUND: '예약을 찾을 수 없습니다.',
  EMAIL_ALREADY_EXISTS: '이미 가입된 이메일입니다.',
  RESERVATION_SLOT_CONFLICT: '방금 다른 사용자가 예약한 시간입니다. 다른 시간을 선택해 주세요.',
  RESERVATION_STATE_CONFLICT: '이미 처리되었거나 만료된 예약입니다. 새로고침 후 확인해 주세요.',
  IDEMPOTENCY_KEY_CONFLICT: '이전 요청과 내용이 다릅니다. 처음부터 다시 시도해 주세요.',
  SPACE_INACTIVE: '현재 신규 예약을 받지 않는 공간입니다.',
  SPACE_VERSION_MISMATCH: '결제를 기다리는 동안 공간 요금이 변경되었습니다. 처음부터 다시 예약해 주세요.',
  SPACE_OPERATING_HOURS_CONFLICT: '진행 중이거나 예정된 예약이 있어 운영시간을 줄일 수 없습니다.',
  INSUFFICIENT_BALANCE: '크레딧 잔액이 부족합니다. 예약은 결제 대기 상태로 유지되며, 만료 전까지 다시 시도할 수 있습니다.',
  SELF_GRANT_NOT_ALLOWED: '자기 자신에게는 크레딧을 지급할 수 없습니다.',
  TARGET_IS_ADMIN: '관리자 계정은 정지하거나 복구할 수 없습니다.',
  RESERVATION_NOT_CONFIRMED: '취소되었거나 이미 종료된 예약입니다.',
  NETWORK_ERROR: '서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.',
};

const STATUS_FALLBACK: Record<number, string> = {
  400: '요청 내용을 확인해 주세요.',
  401: '로그인이 필요합니다.',
  403: '접근 권한이 없습니다.',
  404: '요청한 정보를 찾을 수 없습니다.',
  409: '현재 상태와 충돌하는 요청입니다.',
  422: '처리할 수 없는 요청입니다.',
  500: '서버에서 오류가 발생했습니다.',
};

/** 화면이 넘길 수 있는 오류의 최소 모양. ApiError 가 이 모양을 만족합니다. */
export interface MessageSource {
  code?: string;
  status?: number;
}

/** ApiError 또는 코드 문자열을 사용자 문구로 바꿉니다. */
export function toMessage(
  error: MessageSource | string | null | undefined,
  fallback = '요청을 처리하지 못했습니다.',
): string {
  if (!error) return fallback;
  const code = typeof error === 'string' ? error : error.code;
  const message = code ? MESSAGES[code as ErrorCode] : undefined;
  if (message) return message;
  const status = typeof error === 'object' ? error.status : undefined;
  const statusMessage = status ? STATUS_FALLBACK[status] : undefined;
  if (statusMessage) return statusMessage;
  return fallback;
}
