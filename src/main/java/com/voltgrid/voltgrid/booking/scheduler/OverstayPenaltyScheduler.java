package com.voltgrid.voltgrid.booking.scheduler;

import com.voltgrid.voltgrid.booking.service.OverstayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Step 20: Overstay Penalty Scheduler.
 * Accrues overstay penalties on active sessions past the grace period.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverstayPenaltyScheduler {

    private final OverstayService overstayService;

    @Scheduled(fixedDelayString = "${app.overstay.check-interval-ms:60000}")
    public void runOverstayAccrualJob() {
        overstayService.accrueOverstayPenalties();
    }
}
