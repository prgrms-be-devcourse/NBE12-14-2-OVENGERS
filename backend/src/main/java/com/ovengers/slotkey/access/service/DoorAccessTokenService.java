package com.ovengers.slotkey.access.service;

import com.ovengers.slotkey.access.authorization.DoorAccessAuthorizationService;
import com.ovengers.slotkey.access.dto.response.DoorAccessTokenResponse;
import com.ovengers.slotkey.access.entity.DoorAccessToken;
import com.ovengers.slotkey.access.policy.DoorAccessTimePolicy;
import com.ovengers.slotkey.access.repository.DoorAccessTokenRepository;
import com.ovengers.slotkey.access.support.AccessTokenGenerator;
import com.ovengers.slotkey.access.support.AccessTokenHasher;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DoorAccessTokenService {

    private final DoorAccessTokenRepository doorAccessTokenRepository;
    private final ReservationRepository reservationRepository;
    private final DoorAccessAuthorizationService doorAccessAuthorizationService;
    private final DoorAccessTimePolicy doorAccessTimePolicy;
    private final AccessTokenGenerator accessTokenGenerator;
    private final AccessTokenHasher accessTokenHasher;
    private final Clock clock;

    // 새로운 출입 토큰 생성
    @Transactional
    public DoorAccessToken create(
            Reservation reservation,
            String tokenHash,
            LocalDateTime issuedAt
    ) {
        DoorAccessToken token = new DoorAccessToken(
                reservation,
                tokenHash,
                issuedAt
        );

        return doorAccessTokenRepository.save(token);
    }

    // 예약에 사용할 출입 토큰 발급
    @Transactional
    public DoorAccessTokenResponse issue(
            Long loginMemberId,
            Long reservationId
    ) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESERVATION_NOT_FOUND
                ));

        // 예약 소유자 확인
        doorAccessAuthorizationService.validateOwner(
                loginMemberId,
                reservation.getMemberId()
        );

        // // 확정 또는 이용 중인 예약인지 확인
        ReservationStatus status = reservation.getStatus();

        if (status != ReservationStatus.CONFIRMED && status != ReservationStatus.IN_USE) {
            throw new BusinessException(
                    ErrorCode.RESERVATION_STATE_CONFLICT
            );
        }

        LocalDateTime issuedAt =
                LocalDateTime.now(clock);

        // 예약 종료 전인지 확인
        if (!doorAccessTimePolicy.canIssueToken(
                issuedAt,
                reservation.getEndTime()
        )) {
            throw new BusinessException(
                    ErrorCode.RESERVATION_STATE_CONFLICT
            );
        }

        // 기존 활성 토큰이 있으면 먼저 폐기
        doorAccessTokenRepository.findByReservationIdAndRevokedAtIsNull(reservationId)
                .ifPresent(activeToken ->
                        activeToken.revoke(
                                issuedAt,
                                "REISSUED"
                        )
                );

        doorAccessTokenRepository.flush();

        // 새로운 원문 토큰 생성
        String rawToken =
                accessTokenGenerator.generate();

        // 원문 토큰을 SHA-256으로 해시
        String tokenHash =
                accessTokenHasher.hash(rawToken);

        // DB에는 해시된 토큰 저장. 같은 예약에 발급 요청이 동시에 들어오면 활성 토큰 UNIQUE 제약
        // (active_reservation_id)이 하나만 통과시키므로, 진 요청은 500이 아니라 409로 돌려준다.
        try {
            create(
                    reservation,
                    tokenHash,
                    issuedAt
            );
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    ErrorCode.RESERVATION_STATE_CONFLICT,
                    "동시에 다른 발급 요청이 처리되었습니다. 다시 시도해주세요."
            );
        }

        // 사용자에게는 원문 토큰 반환
        return DoorAccessTokenResponse.of(
                reservationId,
                rawToken,
                issuedAt
        );
    }

    // 출입 토큰 단건 조회
    public DoorAccessToken findById(Long tokenId) {
        return doorAccessTokenRepository.findById(tokenId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCESS_TOKEN_NOT_FOUND
                ));
    }

    // 토큰 해시로 단건 조회
    public DoorAccessToken findByTokenHash(String tokenHash) {
        return doorAccessTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCESS_TOKEN_NOT_FOUND
                ));
    }

    // 원문 토큰으로 단건 조회
    public DoorAccessToken findByRawToken(String rawToken) {
        String tokenHash =
                accessTokenHasher.hash(rawToken);

        return findByTokenHash(tokenHash);
    }

    // 원문 토큰으로 조회하되, 검증 실패를 정상적인 거절 결과로 처리할 수 있도록 Optional 반환
    public Optional<DoorAccessToken> findOptionalByRawToken(String rawToken) {
        String tokenHash =
                accessTokenHasher.hash(rawToken);

        return doorAccessTokenRepository.findByTokenHash(tokenHash);
    }

    // 특정 예약의 활성 토큰 조회
    public DoorAccessToken findActiveByReservationId(Long reservationId) {
        return doorAccessTokenRepository.findByReservationIdAndRevokedAtIsNull(reservationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACTIVE_ACCESS_TOKEN_NOT_FOUND
                ));
    }

    // 출입 토큰 전체 조회
    public List<DoorAccessToken> findAll() {
        return doorAccessTokenRepository.findAll();
    }

    // 출입 토큰 ID로 폐기
    @Transactional
    public DoorAccessToken revoke(
            Long tokenId,
            LocalDateTime revokedAt,
            String revokeReason
    ) {
        DoorAccessToken token =
                findById(tokenId);

        token.revoke(
                revokedAt,
                revokeReason
        );

        return token;
    }

    // 예약 소유자가 활성 출입 토큰 폐기
    @Transactional
    public void revokeByReservationId(
            Long loginMemberId,
            Long reservationId,
            LocalDateTime revokedAt,
            String revokeReason
    ) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESERVATION_NOT_FOUND
                ));

        doorAccessAuthorizationService.validateOwner(
                loginMemberId,
                reservation.getMemberId()
        );

        DoorAccessToken token =
                findActiveByReservationId(reservationId);

        token.revoke(
                revokedAt,
                revokeReason
        );
    }

    // 예약 상태 변경에 따른 활성 출입 토큰 폐기
    @Transactional
    public void revokeByReservation(
            Long reservationId,
            LocalDateTime revokedAt,
            String revokeReason
    ) {
        doorAccessTokenRepository
                .findByReservationIdAndRevokedAtIsNull(
                        reservationId
                )
                .ifPresent(token ->
                        token.revoke(
                                revokedAt,
                                revokeReason
                        )
                );
    }


    // ---------- validateExtension ----------

    private static final LocalDateTime EXTEND_FROM = LocalDateTime.of(2026, 9, 18, 21, 0);

    @Test
    @DisplayName("연장 종료 시각이 운영 종료 시각과 정확히 같으면 통과한다")
    void validateExtension_untilExactlyClosing_doesNotThrow() {
        LocalDateTime newEnd = LocalDateTime.of(2026, 9, 18, 22, 0);

        assertThatCode(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, newEnd, CLOSING))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("연장 종료 시각이 운영 종료를 한 슬롯(30분)이라도 넘으면 INVALID_RESERVATION_TIME 예외가 발생한다")
    void validateExtension_oneSlotPastClosing_throwsInvalidTime() {
        LocalDateTime newEnd = LocalDateTime.of(2026, 9, 18, 22, 30);

        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, newEnd, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("연장 종료 시각이 30분 단위가 아니면(분이 어긋나거나 초가 있으면) INVALID_RESERVATION_TIME 예외가 발생한다")
    void validateExtension_unalignedEndTime_throwsInvalidTime() {
        LocalDateTime unalignedMinute = LocalDateTime.of(2026, 9, 18, 21, 15);
        LocalDateTime withSeconds = LocalDateTime.of(2026, 9, 18, 21, 30, 10);

        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, unalignedMinute, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, withSeconds, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("연장으로 날짜가 넘어가면(자정 포함) INVALID_RESERVATION_TIME 예외가 발생한다")
    void validateExtension_crossesDate_throwsInvalidTime() {
        LocalDateTime midnight = LocalDateTime.of(2026, 9, 19, 0, 0);
        LocalDateTime nextDay = LocalDateTime.of(2026, 9, 19, 10, 0);

        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, midnight, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, nextDay, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("연장 종료 시각이 기존 종료 시각과 같거나 이르면 VALIDATION_FAILED 예외가 발생한다")
    void validateExtension_notAfterCurrentEnd_throwsValidationFailed() {
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, EXTEND_FROM, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(
                EXTEND_FROM, EXTEND_FROM.minusMinutes(30), CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("연장 시각 정보가 null이면 VALIDATION_FAILED 예외가 발생한다")
    void validateExtension_nullTime_throwsValidationFailed() {
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(null, EXTEND_FROM, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, null, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("운영 시간 안에서 30분 단위로 같은 날 연장하면 예외 없이 통과한다")
    void validateExtension_validExtension_doesNotThrow() {
        LocalDateTime newEnd = LocalDateTime.of(2026, 9, 18, 21, 30);

        assertThatCode(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, newEnd, CLOSING))
                .doesNotThrowAnyException();
    }

}
