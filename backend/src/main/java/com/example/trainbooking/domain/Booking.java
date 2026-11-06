package com.example.trainbooking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_number", nullable = false, unique = true)
    private Integer seatNumber;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    protected Booking() {}

    public Booking(Integer seatNumber, UUID reservationId, String userId, Instant bookedAt) {
        this.seatNumber = seatNumber;
        this.reservationId = reservationId;
        this.userId = userId;
        this.bookedAt = bookedAt;
    }

    public Integer getSeatNumber() { return seatNumber; }
    public UUID getReservationId() { return reservationId; }
    public String getUserId() { return userId; }
    public Instant getBookedAt() { return bookedAt; }
}
