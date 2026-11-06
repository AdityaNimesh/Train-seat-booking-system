package com.example.trainbooking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases seats whose 2-minute lock has expired. Correctness never depends on it (see Seat.isAvailableAt). */
@Component
public class LockExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(LockExpiryJob.class);
    private final BookingService bookingService;

    public LockExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${app.expiry-sweep-ms:5000}")
    public void sweep() {
        try {
            int released = bookingService.expireStaleLocks();
            if (released > 0) {
                log.info("Released {} expired seat lock(s)", released);
            }
        } catch (RuntimeException e) {
            log.warn("Expiry sweep skipped, will retry: {}", e.getMessage());
        }
    }
}
