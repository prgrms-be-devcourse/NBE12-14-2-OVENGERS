package com.ovengers.slotkey.stats.controller;

import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.entity.SpaceRankingSnapshot;
import com.ovengers.slotkey.stats.repository.SpaceRankingSnapshotRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import com.ovengers.slotkey.support.SettableClockConfig;
import com.ovengers.slotkey.support.SettableClockConfig.SettableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 일반 회원용 랭킹 API(api-spec §14-2). 순매출은 내려주지 않고 정렬 기준도 건수·슬롯 수만 허용한다. 기준 시각은 2026-10-16(금) 10:00 KST. */
@AutoConfigureMockMvc
@Transactional
@Import(SettableClockConfig.class)
class StatsControllerIntegrationTest extends IntegrationTestSupport {

    private static final String SPACE_RANKING = "/api/v1/stats/space-ranking";
    private static final String LOCATION_RANKING = "/api/v1/stats/location-ranking";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private SpaceRankingSnapshotRepository snapshotRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private SettableClock settableClock;

    private String userToken;

    @BeforeEach
    void setUp() {
        settableClock.set(ZonedDateTime.of(2026, 10, 16, 10, 0, 0, 0, SettableClockConfig.KST));
        userToken = bearer(saveMember(MemberRole.USER));
    }

    @Test
    @DisplayName("미인증 요청은 401 AUTHENTICATION_REQUIRED이다")
    void unauthenticated() throws Exception {
        mockMvc.perform(get(SPACE_RANKING).param("period", "DAILY").param("date", "2026-10-14"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get(LOCATION_RANKING).param("period", "DAILY").param("date", "2026-10-14"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("USER 역할도 공간 랭킹을 볼 수 있고 순매출 필드는 응답에 없으며 기본 정렬은 예약 건수이다")
    void userCanSeeSpaceRankingWithoutRevenue() throws Exception {
        Long a = saveSpace("판교 A룸", "판교");
        Long b = saveSpace("판교 B룸", "판교");
        snap(a, "판교", 99_000, 2, 4);
        snap(b, "판교", 100, 5, 6);

        mockMvc.perform(get(SPACE_RANKING).header(HttpHeaders.AUTHORIZATION, userToken)
                        .param("period", "DAILY").param("date", "2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metric").value("RESERVATION_COUNT"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].spaceId").value(b))
                .andExpect(jsonPath("$.data.items[0].spaceName").value("판교 B룸"))
                .andExpect(jsonPath("$.data.items[0].location").value("판교"))
                .andExpect(jsonPath("$.data.items[0].reservationCount").value(5))
                .andExpect(jsonPath("$.data.items[0].slotCount").value(6))
                .andExpect(jsonPath("$.data.items[0].netRevenue").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].spaceId").value(a));
    }

    @Test
    @DisplayName("지역 랭킹도 순매출 없이 지역·오피스 수·건수·슬롯 수만 내려준다")
    void locationRankingWithoutRevenue() throws Exception {
        snap(saveSpace("A", "판교"), "판교", 100, 1, 2);
        snap(saveSpace("B", "판교"), "판교", 200, 2, 3);

        mockMvc.perform(get(LOCATION_RANKING).header(HttpHeaders.AUTHORIZATION, userToken)
                        .param("period", "DAILY").param("date", "2026-10-14").param("metric", "SLOT_COUNT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metric").value("SLOT_COUNT"))
                .andExpect(jsonPath("$.data.items[0].location").value("판교"))
                .andExpect(jsonPath("$.data.items[0].spaceCount").value(2))
                .andExpect(jsonPath("$.data.items[0].reservationCount").value(3))
                .andExpect(jsonPath("$.data.items[0].slotCount").value(5))
                .andExpect(jsonPath("$.data.items[0].netRevenue").doesNotExist());
    }

    @Test
    @DisplayName("NET_REVENUE 정렬은 400 VALIDATION_FAILED이다(순매출 순서가 노출되지 않게)")
    void netRevenueMetricRejected() throws Exception {
        mockMvc.perform(get(SPACE_RANKING).header(HttpHeaders.AUTHORIZATION, userToken)
                        .param("period", "DAILY").param("date", "2026-10-14").param("metric", "NET_REVENUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get(LOCATION_RANKING).header(HttpHeaders.AUTHORIZATION, userToken)
                        .param("period", "DAILY").param("date", "2026-10-14").param("metric", "NET_REVENUE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("period·date 누락은 400이고 진행 중인 기간은 PENDING과 confirmAt을 내려준다")
    void requiredParamsAndPending() throws Exception {
        mockMvc.perform(get(SPACE_RANKING).header(HttpHeaders.AUTHORIZATION, userToken).param("date", "2026-10-14"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(SPACE_RANKING).header(HttpHeaders.AUTHORIZATION, userToken)
                        .param("period", "DAILY").param("date", "2026-10-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.confirmAt").value("2026-10-17T00:00:00"))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    // ---------- helpers ----------

    private Member saveMember(MemberRole role) {
        Member member = new Member(role.name().toLowerCase() + "-" + System.nanoTime() + "@test.com", "stats-test");
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        return memberRepository.saveAndFlush(member);
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.genAccessToken(member);
    }

    private Long saveSpace(String name, String location) {
        return spaceRepository.saveAndFlush(Space.builder()
                .name(name)
                .location(location)
                .capacity(4)
                .pricePerSlot(5000L)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(SpaceStatus.ACTIVE)
                .version(0)
                .build()).getId();
    }

    private void snap(Long spaceId, String location, long revenue, int count, int slots) {
        LocalDate start = LocalDate.of(2026, 10, 14);
        snapshotRepository.saveAndFlush(new SpaceRankingSnapshot(RankingPeriodType.DAILY, start, spaceId, location,
                revenue, count, slots, start.plusDays(1).atTime(0, 1)));
    }
}
