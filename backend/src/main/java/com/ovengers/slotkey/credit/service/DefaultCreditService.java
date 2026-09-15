package com.ovengers.slotkey.credit.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * credit 도메인(백한비님 담당)이 아직 구현되지 않았을 때 앱이 최소한 부팅되도록 하는
 * 기본 구현체. 실제로 결제 확인/연장 요청이 들어오면 즉시 실패시킨다 — 돈이 움직이는
 * 작업을 조용히 성공한 척 처리해서는 안 되기 때문이다(§0 설계 원칙 4).
 *
 * credit 도메인에서 실제 CreditService 구현체(원장 기록 + 조건부 UPDATE)를 빈으로
 * 등록하면 @ConditionalOnMissingBean에 의해 이 기본 구현은 자동으로 비활성화된다.
 * (space.service.DefaultOccupiedSlotProvider와 동일한 패턴.)
 */
@Component
@ConditionalOnMissingBean(CreditService.class)
public class DefaultCreditService implements CreditService {

    @Override
    public int charge(Long memberId, Long reservationId, int amount) {
        throw new IllegalStateException(
                "credit 도메인이 아직 구현되지 않았습니다. credit_transaction 원장을 기록하는 실제 " +
                        "CreditService 구현체가 빈으로 등록되면 이 기본 구현은 자동으로 대체됩니다."
        );
    }
}
