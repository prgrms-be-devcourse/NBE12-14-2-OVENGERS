package com.ovengers.slotkey.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.error.GlobalExceptionHandler;
import com.ovengers.slotkey.space.dto.request.SpaceSearchCondition;
import com.ovengers.slotkey.space.dto.response.SlotResponse;
import com.ovengers.slotkey.space.dto.response.SpaceSlotAvailabilityResponse;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.service.SpaceQueryService;
import com.ovengers.slotkey.space.service.SpaceSlotAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SpaceControllerTest {

        private MockMvc mockMvc;

        @Mock
        private SpaceQueryService spaceQueryService;

        @Mock
        private SpaceSlotAvailabilityService slotAvailabilityService;

        @InjectMocks
        private SpaceController spaceController;

        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());

                mockMvc = MockMvcBuilders.standaloneSetup(spaceController)
                                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();
        }

        private Space createSpace(Long id, String name) {
                return Space.builder()
                                .id(id)
                                .name(name)
                                .location("서울시 강남구 테헤란로")
                                .description("회의실 설명")
                                .capacity(6)
                                .pricePerSlot(5000L)
                                .imagePath("/img/room.jpg")
                                .openingTime(LocalTime.of(9, 0))
                                .closingTime(LocalTime.of(18, 0))
                                .status(SpaceStatus.ACTIVE)
                                .version(1)
                                .build();
        }

        @Test
        @DisplayName("GET /api/v1/spaces 기본 요청 시 200 OK와 PageResponse 규격의 공간 목록을 반환한다")
        void getSpacesPage_default_returnsPageResponse() throws Exception {
                // given
                Pageable pageable = PageRequest.of(0, 20);
                List<Space> spaces = List.of(createSpace(1L, "회의실 A"), createSpace(2L, "회의실 B"));
                Page<Space> page = new PageImpl<>(spaces, pageable, spaces.size());

                SpaceSearchCondition expectedCondition =
                                new SpaceSearchCondition(null, null, null, null, null, null, null);
                given(spaceQueryService.getSpacesPage(any(Pageable.class), eq(expectedCondition))).willReturn(page);

                // when & then
                mockMvc.perform(get("/api/v1/spaces")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.code").value("OK"))
                                .andExpect(jsonPath("$.data.content").isArray())
                                .andExpect(jsonPath("$.data.content.length()").value(2))
                                .andExpect(jsonPath("$.data.content[0].id").value(1L))
                                .andExpect(jsonPath("$.data.content[0].name").value("회의실 A"))
                                .andExpect(jsonPath("$.data.content[1].id").value(2L))
                                .andExpect(jsonPath("$.data.content[1].name").value("회의실 B"))
                                .andExpect(jsonPath("$.data.page").value(0))
                                .andExpect(jsonPath("$.data.size").value(20))
                                .andExpect(jsonPath("$.data.totalElements").value(2))
                                .andExpect(jsonPath("$.data.totalPages").value(1));

                ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
                verify(spaceQueryService).getSpacesPage(pageableCaptor.capture(), eq(expectedCondition));
                assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("GET /api/v1/spaces 요청 시 keyword, location, 가격 범위, page, size 파라미터가 서비스에 올바르게 전달된다")
        void getSpacesPage_withFiltersAndPaging_success() throws Exception {
                // given
                Pageable pageable = PageRequest.of(1, 10);
                List<Space> spaces = List.of(
                        createSpace(3L, "강남 회의실 A"),
                        createSpace(4L, "강남 회의실 B"),
                        createSpace(5L, "강남 회의실 C"),
                        createSpace(6L, "강남 회의실 D"),
                        createSpace(7L, "강남 회의실 E")
                );
                Page<Space> page = new PageImpl<>(spaces, pageable, 15);

                SpaceSearchCondition expectedCondition =
                                new SpaceSearchCondition("강남", "강남", 3000L, 8000L, null, null, null);
                given(spaceQueryService.getSpacesPage(any(Pageable.class), eq(expectedCondition))).willReturn(page);

                // when & then
                mockMvc.perform(get("/api/v1/spaces")
                                .param("keyword", "강남")
                                .param("location", "강남")
                                .param("minPrice", "3000")
                                .param("maxPrice", "8000")
                                .param("page", "1")
                                .param("size", "10")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.data.content.length()").value(5))
                                .andExpect(jsonPath("$.data.content[0].name").value("강남 회의실 A"))
                                .andExpect(jsonPath("$.data.page").value(1))
                                .andExpect(jsonPath("$.data.size").value(10))
                                .andExpect(jsonPath("$.data.totalElements").value(15))
                                .andExpect(jsonPath("$.data.totalPages").value(2));

                ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
                verify(spaceQueryService).getSpacesPage(pageableCaptor.capture(), eq(expectedCondition));
                assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
                assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("GET /api/v1/spaces 요청 시 date/startTime/endTime이 서비스에 올바르게 전달된다")
        void getSpacesPage_withTimeFilter_success() throws Exception {
                // given
                Pageable pageable = PageRequest.of(0, 20);
                List<Space> spaces = List.of(createSpace(1L, "회의실 A"));
                Page<Space> page = new PageImpl<>(spaces, pageable, spaces.size());

                SpaceSearchCondition expectedCondition = new SpaceSearchCondition(
                                null, null, null, null,
                                LocalDate.of(2026, 9, 23), LocalTime.of(14, 0), LocalTime.of(15, 0));
                given(spaceQueryService.getSpacesPage(any(Pageable.class), eq(expectedCondition))).willReturn(page);

                // when & then
                mockMvc.perform(get("/api/v1/spaces")
                                .param("date", "2026-09-23")
                                .param("startTime", "14:00:00")
                                .param("endTime", "15:00:00")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content.length()").value(1));

                verify(spaceQueryService).getSpacesPage(any(Pageable.class), eq(expectedCondition));
        }

        @Test
        @DisplayName("GET /api/v1/spaces/{id} 성공 시 200 OK와 공간 상세 정보(version 포함)를 반환한다")
        void getSpaceDetailById_success() throws Exception {
                // given
                Long spaceId = 1L;
                Space space = createSpace(spaceId, "회의실 A");
                given(spaceQueryService.getSpaceDetailById(spaceId)).willReturn(space);

                // when & then
                mockMvc.perform(get("/api/v1/spaces/{spaceId}", spaceId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.code").value("OK"))
                                .andExpect(jsonPath("$.data.id").value(spaceId))
                                .andExpect(jsonPath("$.data.name").value("회의실 A"))
                                .andExpect(jsonPath("$.data.location").value("서울시 강남구 테헤란로"))
                                .andExpect(jsonPath("$.data.capacity").value(6))
                                .andExpect(jsonPath("$.data.pricePerSlot").value(5000L))
                                .andExpect(jsonPath("$.data.version").value(1))
                                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

                verify(spaceQueryService).getSpaceDetailById(spaceId);
        }

        @Test
        @DisplayName("GET /api/v1/spaces/{id} 미존재 공간 조회 시 404 Not Found와 SPACE_NOT_FOUND 에러를 반환한다")
        void getSpaceDetailById_notFound() throws Exception {
                // given
                Long notFoundId = 999L;
                given(spaceQueryService.getSpaceDetailById(notFoundId))
                                .willThrow(new BusinessException(ErrorCode.SPACE_NOT_FOUND));

                // when & then
                mockMvc.perform(get("/api/v1/spaces/{spaceId}", notFoundId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value("FAIL"))
                                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));

                verify(spaceQueryService).getSpaceDetailById(notFoundId);
        }

        @Test
        @DisplayName("GET /api/v1/spaces/{id}/slots 조회 시 200 OK와 슬롯 가용성 목록을 반환한다")
        void getSlotAvailability_success() throws Exception {
                // given
                Long spaceId = 1L;
                LocalDate date = LocalDate.of(2026, 9, 20);

                List<SlotResponse> slots = List.of(
                                SlotResponse.of(
                                                LocalDateTime.of(2026, 9, 20, 9, 0),
                                                LocalDateTime.of(2026, 9, 20, 9, 30),
                                                false),
                                SlotResponse.of(
                                                LocalDateTime.of(2026, 9, 20, 9, 30),
                                                LocalDateTime.of(2026, 9, 20, 10, 0),
                                                true));

                SpaceSlotAvailabilityResponse response = SpaceSlotAvailabilityResponse.of(spaceId, date, slots);
                given(slotAvailabilityService.getSlotAvailability(spaceId, date)).willReturn(response);

                // when & then
                mockMvc.perform(get("/api/v1/spaces/{spaceId}/slots", spaceId)
                                .param("date", "2026-09-20")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.code").value("OK"))
                                .andExpect(jsonPath("$.data.spaceId").value(spaceId))
                                .andExpect(jsonPath("$.data.date").value("2026-09-20"))
                                .andExpect(jsonPath("$.data.slots").isArray())
                                .andExpect(jsonPath("$.data.slots.length()").value(2))
                                .andExpect(jsonPath("$.data.slots[0].slotStart").value("2026-09-20T09:00:00"))
                                .andExpect(jsonPath("$.data.slots[0].slotEnd").value("2026-09-20T09:30:00"))
                                .andExpect(jsonPath("$.data.slots[0].isAvailable").value(false))
                                .andExpect(jsonPath("$.data.slots[1].slotStart").value("2026-09-20T09:30:00"))
                                .andExpect(jsonPath("$.data.slots[1].slotEnd").value("2026-09-20T10:00:00"))
                                .andExpect(jsonPath("$.data.slots[1].isAvailable").value(true));

                verify(slotAvailabilityService).getSlotAvailability(spaceId, date);
        }
}
