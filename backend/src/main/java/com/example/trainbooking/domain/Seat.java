package com.example.trainbooking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @Column(name = "seat_number")
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected Seat() {}

    public Integer getSeatNumber() { return seatNumber; }
    public SeatStatus getStatus() { return status; }
    public UUID getReservationId() { return reservationId; }
    public Instant getLockedUntil() { return lockedUntil; }

    /** A seat can be taken if it is free, or if its lock has already run out. */
    public boolean isAvailableAt(Instant now) {
        return status == SeatStatus.AVAILABLE
                || (status == SeatStatus.LOCKED && lockedUntil != null && !lockedUntil.isAfter(now));
    }

    /** Status as users should see it: an expired lock counts as available even before the sweeper runs. */
    public SeatStatus effectiveStatus(Instant now) {
        return isAvailableAt(now) ? SeatStatus.AVAILABLE : status;
    }

    public void lock(UUID reservationId, Instant until) {
        this.status = SeatStatus.LOCKED;
        this.reservationId = reservationId;
        this.lockedUntil = until;
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.reservationId = null;
        this.lockedUntil = null;
    }

    public void book() {
        this.status = SeatStatus.BOOKED;
        this.lockedUntil = null;
    }
}
