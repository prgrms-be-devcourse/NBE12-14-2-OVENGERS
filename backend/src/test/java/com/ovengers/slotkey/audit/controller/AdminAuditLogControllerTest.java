package com.ovengers.slotkey.audit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ovengers.slotkey.audit.dto.AdminAuditLogSearchCondition;
import com.ovengers.slotkey.audit.dto.AuditLogResponse;
import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.service.AuditLogQueryService;
import com.ovengers.slotkey.global.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuditLogQueryService auditLogQueryService;

    @InjectMocks
    private AdminAuditLogController adminAuditLogController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(adminAuditLogController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit-logs 기본 요청 시 200 OK와 PageResponse 규격의 감사 로그 목록을 반환한다")
    void getAuditLogs_default_returnsPageResponse() throws Exception {
        // given
        AuditLogResponse logResponse = new AuditLogResponse(
                1L,
                10L,
                AuditAction.REGISTER_SPACE,
                AuditTargetType.SPACE,
                100L,
                "신규 회의실 등록",
                null,
                "{\"name\":\"회의실 A\"}",
                LocalDateTime.of(2026, 9, 20, 10, 0, 0)
        );
        Page<AuditLogResponse> page = new PageImpl<>(List.of(logResponse), PageRequest.of(0, 20), 1);
        given(auditLogQueryService.searchAuditLogs(any(), any(Pageable.class))).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(1L))
                .andExpect(jsonPath("$.data.content[0].actorMemberId").value(10L))
                .andExpect(jsonPath("$.data.content[0].action").value("REGISTER_SPACE"))
                .andExpect(jsonPath("$.data.content[0].targetType").value("SPACE"))
                .andExpect(jsonPath("$.data.content[0].targetId").value(100L))
                .andExpect(jsonPath("$.data.content[0].reason").value("신규 회의실 등록"))
                .andExpect(jsonPath("$.data.content[0].beforeValue").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.content[0].afterValue").value("{\"name\":\"회의실 A\"}"))
                .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-09-20T10:00:00"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogQueryService).searchAuditLogs(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("6개 필터와 페이징 파라미터가 SearchCondition과 Pageable로 정확하게 매핑되어 서비스로 전달된다")
    void getAuditLogs_withAllFilters_bindsCorrectly() throws Exception {
        // given
        Page<AuditLogResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(2, 10), 0);
        given(auditLogQueryService.searchAuditLogs(any(AdminAuditLogSearchCondition.class), any(Pageable.class)))
                .willReturn(emptyPage);

        // when & then
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("actorMemberId", "5")
                        .param("action", "MODIFY_SPACE")
                        .param("targetType", "SPACE")
                        .param("targetId", "50")
                        .param("dateFrom", "2026-09-10")
                        .param("dateTo", "2026-09-20")
                        .param("page", "2")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10));

        ArgumentCaptor<AdminAuditLogSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(AdminAuditLogSearchCondition.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(auditLogQueryService).searchAuditLogs(conditionCaptor.capture(), pageableCaptor.capture());

        AdminAuditLogSearchCondition captured = conditionCaptor.getValue();
        assertThat(captured.actorMemberId()).isEqualTo(5L);
        assertThat(captured.action()).isEqualTo(AuditAction.MODIFY_SPACE);
        assertThat(captured.targetType()).isEqualTo(AuditTargetType.SPACE);
        assertThat(captured.targetId()).isEqualTo(50L);
        assertThat(captured.dateFrom()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(captured.dateTo()).isEqualTo(LocalDate.of(2026, 9, 20));

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("잘못된 enum 값이 전달되면 400 Bad Request와 VALIDATION_FAILED를 반환한다")
    void getAuditLogs_invalidEnum_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("action", "INVALID_ACTION_ENUM")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(auditLogQueryService);
    }

    @Test
    @DisplayName("잘못된 날짜 형식이 전달되면 400 Bad Request와 VALIDATION_FAILED를 반환한다")
    void getAuditLogs_invalidDateFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("dateFrom", "not-a-date")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(auditLogQueryService);
    }
}
