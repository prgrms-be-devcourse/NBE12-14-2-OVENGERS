package com.ovengers.slotkey.payment.service;

import com.ovengers.slotkey.payment.dto.response.PaymentResponse;
import com.ovengers.slotkey.payment.entity.Payment;
import com.ovengers.slotkey.payment.gateway.PaymentGateway;
import com.ovengers.slotkey.payment.repository.PaymentRepository;
import com.ovengers.slotkey.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    // 결제 생성
    @Transactional
    public PaymentResponse create(
            Reservation reservation,
            Long amount,
            LocalDateTime paidAt
    ) {

        Payment payment = new Payment(reservation, amount, paidAt);

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentResponse.from(savedPayment);
    }

    // 예약 ID로 결제 조회
    public PaymentResponse findByReservationId(Long reservationId) {

        Payment payment = paymentRepository.findByReservation_Id(reservationId)
                .orElseThrow(() -> new NoSuchElementException("결제 정보를 찾을 수 없습니다."));

        return PaymentResponse.from(payment);
    }

    // 결제 취소
    @Transactional
    public PaymentResponse cancel(
            Long reservationId,
            LocalDateTime cancelledAt
    ) {
        Payment payment = paymentRepository.findByReservation_Id(reservationId)
                .orElseThrow(() -> new NoSuchElementException("결제 정보를 찾을 수 없습니다."));

        payment.cancel(cancelledAt);

        return PaymentResponse.from(payment);
    }
}