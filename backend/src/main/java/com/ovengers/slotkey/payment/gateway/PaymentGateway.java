package com.ovengers.slotkey.payment.gateway;

import com.ovengers.slotkey.payment.gateway.dto.PaymentApproveCommand;
import com.ovengers.slotkey.payment.gateway.dto.PaymentApproveResult;
import com.ovengers.slotkey.payment.gateway.dto.PaymentCancelCommand;
import com.ovengers.slotkey.payment.gateway.dto.PaymentCancelResult;

public interface PaymentGateway {

    PaymentApproveResult approve(PaymentApproveCommand approveCommand);

    PaymentCancelResult cancel(PaymentCancelCommand cancelCommand);
}