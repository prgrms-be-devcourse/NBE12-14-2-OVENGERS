package com.ovengers.slotkey.payment.entity;

import com.ovengers.slotkey.reservation.entity.Reservation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // 예약 1개당 결제 최대 1개 OneToOne
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reservation_id",
            nullable = false,
            unique = true
    )
    private Reservation reservation;

    @Column(nullable = false)
    private Long amount;

    // PaymentStatus enum을 DB에 SUCCESS, CANCELLED 의 문자열로 저장
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private  LocalDateTime cancelledAt;

    // 결제 성공시 새로운 Payment 객체
    public Payment(
            Reservation reservation,
            Long amount,
            LocalDateTime paidAt
    ){
        this.reservation = reservation;
        this.amount = amount;
        this.status = PaymentStatus.SUCCESS;
        this.paidAt = paidAt;
    }

    // 결제 취소
    public void cancel(LocalDateTime cancelledAt){
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }
}
