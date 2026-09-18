package com.ovengers.slotkey.member.dto.request;

import com.ovengers.slotkey.member.entity.MemberStatus;

public record AdminMemberSearchCondition(
        MemberStatus status,
        String keyword
) {
}
