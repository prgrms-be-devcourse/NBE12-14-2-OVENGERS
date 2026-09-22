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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DoorAccessTokenServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long RESERVATION_ID = 10L;
    private static final String RAW_TOKEN = "raw-token";
    private static final String TOKEN_HASH = "hashed-token";

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 10, 0);

    @Mock
    private DoorAccessTokenRepository doorAccessTokenRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DoorAccessAuthorizationService doorAccessAuthorizationService;

    @Mock
    private DoorAccessTimePolicy doorAccessTimePolicy;

    @Mock
    private AccessTokenGenerator accessTokenGenerator;

    @Mock
    private AccessTokenHasher accessTokenHasher;

    @Mock
    private Reservation reservation;

    @Mock
    private DoorAccessToken activeToken;

    private DoorAccessTokenService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        service = new DoorAccessTokenService(
                doorAccessTokenRepository,
                reservationRepository,
                doorAccessAuthorizationService,
                doorAccessTimePolicy,
                accessTokenGenerator,
                accessTokenHasher,
                clock
        );
    }

    @Test
    @DisplayName("확정된 예약에 출입 토큰을 발급한다")
    void shouldIssueTokenForConfirmedReservation() {
        LocalDateTime endAt = NOW.plusHours(1);

        given(reservationRepository.findByIdForUpdate(RESERVATION_ID)).willReturn(Optional.of(reservation));

        given(reservation.getMemberId()).willReturn(MEMBER_ID);

        given(reservation.getStatus()).willReturn(ReservationStatus.CONFIRMED);

        given(reservation.getEndTime()).willReturn(endAt);

        given(doorAccessTimePolicy.canIssueToken(
                NOW,
                endAt
        )).willReturn(true);

        given(doorAccessTokenRepository.findByReservationIdAndRevokedAtIsNullForUpdate(RESERVATION_ID)).willReturn(Optional.empty());

        given(accessTokenGenerator.generate()).willReturn(RAW_TOKEN);

        given(accessTokenHasher.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);

        given(doorAccessTokenRepository.save(any(DoorAccessToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        DoorAccessTokenResponse response = service.issue(
                MEMBER_ID,
                RESERVATION_ID
        );

        assertThat(response.getReservationId()).isEqualTo(RESERVATION_ID);

        assertThat(response.getToken()).isEqualTo(RAW_TOKEN);

        assertThat(response.getIssuedAt()).isEqualTo(NOW);

        ArgumentCaptor<DoorAccessToken> tokenCaptor = ArgumentCaptor.forClass(DoorAccessToken.class);

        verify(doorAccessTokenRepository).save(tokenCaptor.capture());

        DoorAccessToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getReservation()).isEqualTo(reservation);

        assertThat(savedToken.getTokenHash()).isEqualTo(TOKEN_HASH);

        assertThat(savedToken.getIssuedAt()).isEqualTo(NOW);

        verify(doorAccessAuthorizationService).validateOwner(
                MEMBER_ID,
                MEMBER_ID
        );
    }

    @Test
    @DisplayName("기존에 활성화된 출입 토큰이 있으면 폐기하고 재발급한다.")
    void shouldRevokeActiveTokenWhenReissuing() {
        LocalDateTime endAt = NOW.plusHours(1);

        given(reservationRepository.findByIdForUpdate(RESERVATION_ID)).willReturn(Optional.of(reservation));

        given(reservation.getMemberId()).willReturn(MEMBER_ID);

        given(reservation.getStatus()).willReturn(ReservationStatus.CONFIRMED);

        given(reservation.getEndTime()).willReturn(endAt);

        given(doorAccessTimePolicy.canIssueToken(
                NOW,
                endAt
        )).willReturn(true);

        given(doorAccessTokenRepository.findByReservationIdAndRevokedAtIsNullForUpdate(RESERVATION_ID)).willReturn(Optional.of(activeToken));

        given(accessTokenGenerator.generate()).willReturn(RAW_TOKEN);

        given(accessTokenHasher.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);

        service.issue(
                MEMBER_ID,
                RESERVATION_ID
        );

        verify(activeToken).revoke(NOW, "REISSUED");

        verify(doorAccessTokenRepository).save(any(DoorAccessToken.class));
    }

    @Test
    @DisplayName("확정되지 않은 예약에는 출입 토큰을 발급할 수 없다")
    void shouldRejectIssueWhenReservationIsNotConfirmed() {

        given(reservationRepository.findByIdForUpdate(RESERVATION_ID)).willReturn(Optional.of(reservation));

        given(reservation.getMemberId()).willReturn(MEMBER_ID);

        given(reservation.getStatus()).willReturn(ReservationStatus.HELD);

        assertThatThrownBy(() -> service.issue(
                MEMBER_ID,
                RESERVATION_ID
                )
        ).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_STATE_CONFLICT));
    }

    @Test
    @DisplayName("원문 토큰을 해시한 값으로 출입 토큰을 조회한다")
    void shouldFindTokenUsingHashedRawToken() {
        given(accessTokenHasher.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);

        given(doorAccessTokenRepository.findByTokenHash(TOKEN_HASH)).willReturn(Optional.of(activeToken));

        DoorAccessToken result = service.findByRawToken(
                RAW_TOKEN
        );

        assertThat(result).isEqualTo(activeToken);

        verify(accessTokenHasher).hash(RAW_TOKEN);

        verify(doorAccessTokenRepository).findByTokenHash(TOKEN_HASH);
    }

    @Test
    @DisplayName("예약 소유자는 활성 출입 토큰을 폐기할 수 있다")
    void shouldRevokeActiveTokenByReservationOwner() {

        given(reservationRepository.findByIdForUpdate(RESERVATION_ID)).willReturn(Optional.of(reservation));

        given(reservation.getMemberId()).willReturn(MEMBER_ID);

        given(doorAccessTokenRepository.findByReservationIdAndRevokedAtIsNullForUpdate(
                RESERVATION_ID
        )).willReturn(Optional.of(activeToken));

        service.revokeByReservationId(
                MEMBER_ID,
                RESERVATION_ID,
                NOW,
                "사용자 요청"
        );

        verify(doorAccessAuthorizationService).validateOwner(
                MEMBER_ID,
                MEMBER_ID
        );

        verify(activeToken).revoke(NOW, "사용자 요청");
    }
}
