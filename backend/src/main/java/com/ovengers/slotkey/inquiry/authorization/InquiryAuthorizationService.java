package com.ovengers.slotkey.inquiry.authorization;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 문의 소유권 검사. 회원은 본인이 작성한 문의만 조회·수정할 수 있다(관리자 조회/답변은 admin 경로에서 별도 처리). */
@Service
public class InquiryAuthorizationService {

    public void validateOwner(Long loginMemberId, Long inquiryMemberId) {
        if (!Objects.equals(loginMemberId, inquiryMemberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER);
        }
    }
}
