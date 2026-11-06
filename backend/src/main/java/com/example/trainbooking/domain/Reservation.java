package com.example.trainbooking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "reservation_seats", joinColumns = @JoinColumn(name = "reservation_id"))
    @Column(name = "seat_number")
    private Set<Integer> seatNumbers = new LinkedHashSet<>();

    protected Reservation() {}

    public Reservation(String userId, Instant createdAt, Instant expiresAt, Collection<Integer> seatNumbers) {
        this.userId = userId;
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.seatNumbers = new LinkedHashSet<>(seatNumbers);
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Set<Integer> getSeatNumbers() { return seatNumbers; }

    public void setStatus(ReservationStatus status) { this.status = status; }

    public void confirm(Instant now) {
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = now;
    }
}
