package com.ovengers.slotkey.credit.controller;

import com.ovengers.slotkey.credit.dto.response.ChargeOrderResponse;
import com.ovengers.slotkey.credit.dto.response.ChargeOrderConfirmResponse;
import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;
import com.ovengers.slotkey.credit.service.ChargeOrderConfirmService;
import com.ovengers.slotkey.credit.service.ChargeOrderService;
import com.ovengers.slotkey.global.config.SecurityConfig;
import com.ovengers.slotkey.global.config.WebMvcConfig;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.error.GlobalExceptionHandler;
import com.ovengers.slotkey.global.security.CurrentMemberArgumentResolver;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.global.security.jwt.JwtUtil;
import com.ovengers.slotkey.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChargeOrderController.class)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        JwtProvider.class,
        WebMvcConfig.class,
        CurrentMemberArgumentResolver.class,
        GlobalExceptionHandler.class
})
class ChargeOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChargeOrderService chargeOrderService;

    @MockBean
    private ChargeOrderConfirmService chargeOrderConfirmService;

    @Value("${custom.jwt.secret-key}")
    private String secretKey;

    @Test
    @DisplayName("POST /api/v1/credits/charge-orders는 인증 회원의 PENDING 충전 주문을 생성하고 201을 반환한다")
    void create_returnsCreatedPendingOrder() throws Exception {
        String token = JwtUtil.createToken(
                secretKey,
                300_000L,
                Map.of(
                        "id", 7L,
                        "email", "member@test.com",
                        "role", MemberRole.USER.name()
                )
        );
        ChargeOrderResponse response = new ChargeOrderResponse(
                "chg_550e8400-e29b-41d4-a716-446655440000",
                1_000L,
                ChargeOrderStatus.PENDING
        );
        given(chargeOrderService.create(7L, 1_000L)).willReturn(response);

        mockMvc.perform(post("/api/v1/credits/charge-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderId")
                        .value("chg_550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.data.amount").value(1_000L))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(chargeOrderService).create(7L, 1_000L);
    }

    @Test
    @DisplayName("인증 없이 충전 주문을 생성하면 401을 반환한다")
    void create_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/credits/charge-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(chargeOrderService);
    }

    @Test
    @DisplayName("충전 금액이 null이면 400을 반환한다")
    void create_nullAmount_returnsBadRequest() throws Exception {
        mockMvc.perform(authenticatedPost("{\"amount\":null}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chargeOrderService);
    }

    @Test
    @DisplayName("충전 금액이 숫자가 아니면 400을 반환한다")
    void create_nonNumericAmount_returnsBadRequest() throws Exception {
        mockMvc.perform(authenticatedPost("{\"amount\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chargeOrderService);
    }

    @Test
    @DisplayName("충전 금액이 long 범위를 초과하면 400을 반환한다")
    void create_amountExceedsLongRange_returnsBadRequest() throws Exception {
        mockMvc.perform(authenticatedPost("{\"amount\":9223372036854775808}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chargeOrderService);
    }

    @Test
    @DisplayName("승인 완료는 200 PAID를 반환한다")
    void confirm_paid_returnsOk() throws Exception {
        given(chargeOrderConfirmService.confirm(
                7L,
                "payment-key",
                "chg_550e8400-e29b-41d4-a716-446655440000",
                1_000L,
                "request-key"
        )).willReturn(new ChargeOrderConfirmResponse(
                "chg_550e8400-e29b-41d4-a716-446655440000",
                1_000L,
                ChargeOrderStatus.PAID
        ));

        mockMvc.perform(authenticatedConfirm("request-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @DisplayName("결과를 알 수 없는 승인 요청은 202 CONFIRMING을 반환한다")
    void confirm_confirming_returnsAccepted() throws Exception {
        given(chargeOrderConfirmService.confirm(
                7L,
                "payment-key",
                "chg_550e8400-e29b-41d4-a716-446655440000",
                1_000L,
                "request-key"
        )).willReturn(new ChargeOrderConfirmResponse(
                "chg_550e8400-e29b-41d4-a716-446655440000",
                1_000L,
                ChargeOrderStatus.CONFIRMING
        ));

        mockMvc.perform(authenticatedConfirm("request-key"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("CONFIRMING"));
    }

    @Test
    @DisplayName("승인 요청의 Idempotency-Key 헤더가 없으면 400을 반환한다")
    void confirm_withoutIdempotencyKey_returnsBadRequest() throws Exception {
        willThrow(new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED))
                .given(chargeOrderConfirmService)
                .confirm(
                        7L,
                        "payment-key",
                        "chg_550e8400-e29b-41d4-a716-446655440000",
                        1_000L,
                        null
                );

        mockMvc.perform(authenticatedConfirm(null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("승인 요청의 paymentKey가 공백이면 400을 반환한다")
    void confirm_blankPaymentKey_returnsBadRequest() throws Exception {
        mockMvc.perform(authenticatedConfirm("request-key", """
                        {
                          "paymentKey": "   ",
                          "orderId": "chg_550e8400-e29b-41d4-a716-446655440000",
                          "amount": 1000
                        }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chargeOrderConfirmService);
    }

    @Test
    @DisplayName("승인 요청의 orderId가 공백이면 400을 반환한다")
    void confirm_blankOrderId_returnsBadRequest() throws Exception {
        mockMvc.perform(authenticatedConfirm("request-key", """
                        {
                          "paymentKey": "payment-key",
                          "orderId": "   ",
                          "amount": 1000
                        }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chargeOrderConfirmService);
    }

    @Test
    @DisplayName("승인 요청의 amount가 null이면 400을 반환한다")
    void confirm_nullAmount_returnsBadRequest() throws Exception {
        mockMvc.perform(authenticatedConfirm("request-key", """
                        {
                          "paymentKey": "payment-key",
                          "orderId": "chg_550e8400-e29b-41d4-a716-446655440000",
                          "amount": null
                        }
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chargeOrderConfirmService);
    }

    private MockHttpServletRequestBuilder authenticatedPost(
            String content
    ) {
        String token = JwtUtil.createToken(
                secretKey,
                300_000L,
                Map.of(
                        "id", 7L,
                        "email", "member@test.com",
                        "role", MemberRole.USER.name()
                )
        );
        return post("/api/v1/credits/charge-orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
    }

    private MockHttpServletRequestBuilder authenticatedConfirm(String idempotencyKey) {
        return authenticatedConfirm(idempotencyKey, """
                {
                  "paymentKey": "payment-key",
                  "orderId": "chg_550e8400-e29b-41d4-a716-446655440000",
                  "amount": 1000
                }
                """);
    }

    private MockHttpServletRequestBuilder authenticatedConfirm(
            String idempotencyKey,
            String content
    ) {
        String token = JwtUtil.createToken(
                secretKey,
                300_000L,
                Map.of(
                        "id", 7L,
                        "email", "member@test.com",
                        "role", MemberRole.USER.name()
                )
        );
        MockHttpServletRequestBuilder request = post(
                "/api/v1/credits/charge-orders/confirm"
        )
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return request;
    }
}
