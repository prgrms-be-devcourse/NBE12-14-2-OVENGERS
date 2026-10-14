package com.ovengers.slotkey.credit.service;

import com.ovengers.slotkey.credit.dto.response.ChargeOrderConfirmResponse;
import com.ovengers.slotkey.credit.pg.PgConfirmRequest;
import com.ovengers.slotkey.global.error.ErrorCode;

record ChargeOrderPreparation(
        PgConfirmRequest pgRequest,
        ChargeOrderConfirmResponse currentResponse,
        ErrorCode rejection
) {
    static ChargeOrderPreparation callPg(PgConfirmRequest request) {
        return new ChargeOrderPreparation(request, null, null);
    }

    static ChargeOrderPreparation current(ChargeOrderConfirmResponse response) {
        return new ChargeOrderPreparation(null, response, null);
    }

    static ChargeOrderPreparation reject(ErrorCode errorCode) {
        return new ChargeOrderPreparation(null, null, errorCode);
    }

    boolean shouldCallPg() {
        return pgRequest != null;
    }
}
