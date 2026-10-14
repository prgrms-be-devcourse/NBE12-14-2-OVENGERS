package com.ovengers.slotkey.credit.pg;

public interface PgClient {

    PgConfirmationResult confirm(PgConfirmRequest request);

    PgPaymentLookupResult findByPaymentKey(String paymentKey);
}
