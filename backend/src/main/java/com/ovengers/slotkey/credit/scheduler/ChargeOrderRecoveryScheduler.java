package com.ovengers.slotkey.credit.scheduler;

import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;
import com.ovengers.slotkey.credit.repository.ChargeOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(
        prefix = "slotkey.scheduler.charge-recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ChargeOrderRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChargeOrderRecoveryScheduler.class);

    private final ChargeOrderRepository chargeOrderRepository;
    private final ChargeOrderRecoveryProcessor processor;
    private final Clock clock;
    private final int batchSize;

    public ChargeOrderRecoveryScheduler(
            ChargeOrderRepository chargeOrderRepository,
            ChargeOrderRecoveryProcessor processor,
            Clock clock,
            @Value("${slotkey.scheduler.charge-recovery.batch-size:50}") int batchSize
    ) {
        this.chargeOrderRepository = chargeOrderRepository;
        this.processor = processor;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${slotkey.scheduler.charge-recovery.fixed-delay-ms:60000}",
            initialDelayString = "${slotkey.scheduler.charge-recovery.initial-delay-ms:60000}"
    )
    public void recoverChargeOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime recoveryCutoff = now.minusHours(24);
        PageRequest firstBatch = PageRequest.of(0, batchSize);

        List<Long> exhaustedIds = chargeOrderRepository.findRecoveryExhaustedCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                recoveryCutoff,
                firstBatch
        );
        processEach(
                "CHARGE_RECOVERY_EXHAUSTED",
                exhaustedIds,
                id -> processor.markExpired(id, now)
        );

        List<Long> recoveryIds = chargeOrderRepository.findRecoveryCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                now,
                recoveryCutoff,
                firstBatch
        );
        processEach(
                "CHARGE_RECOVERY",
                recoveryIds,
                id -> processor.recover(id, now)
        );
    }

    private void processEach(String jobName, List<Long> chargeOrderIds, Consumer<Long> action) {
        if (chargeOrderIds.isEmpty()) {
            return;
        }
        int completed = 0;
        for (Long chargeOrderId : chargeOrderIds) {
            try {
                action.accept(chargeOrderId);
                completed++;
            } catch (RuntimeException exception) {
                log.error(
                        "[{}] chargeOrderId={} 처리 실패 exceptionClass={}",
                        jobName,
                        chargeOrderId,
                        exception.getClass().getSimpleName(),
                        exception
                );
            }
        }
        log.info(
                "[{}] 대상 {}건 중 {}건 처리",
                jobName,
                chargeOrderIds.size(),
                completed
        );
    }
}
