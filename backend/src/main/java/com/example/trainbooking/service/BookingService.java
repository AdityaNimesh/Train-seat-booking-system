package com.example.trainbooking.service;

import com.example.trainbooking.domain.*;
import com.example.trainbooking.dto.Dtos.*;
import com.example.trainbooking.exception.BookingException;
import com.example.trainbooking.repository.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concurrency strategy
 * --------------------
 * Every state change first takes row locks (SELECT ... FOR UPDATE) inside one transaction:
 *   - reserve : locks the requested seat rows (ascending order), checks all of them, then locks them for the user.
 *   - confirm : locks the reservation row, then its seat rows (same ascending order), then books them.
 *   - cancel  : same lock order as confirm.
 * A second request for the same seat blocks until the first transaction commits, then re-reads the
 * committed state and is rejected with 409. Bookings also have UNIQUE(seat_number) as a final DB-level guard.
 * Reservation is all-or-nothing: if any requested seat is unavailable nothing is locked.
 */
@Service
public class BookingService {

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final Duration lockDuration;

    public BookingService(SeatRepository seatRepository,
                          ReservationRepository reservationRepository,
                          BookingRepository bookingRepository,
                          Clock clock,
                          @Value("${app.lock-duration-seconds:120}") long lockSeconds) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.lockDuration = Duration.ofSeconds(lockSeconds);
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<SeatDto> getSeats() {
        Instant now = clock.instant();
        return seatRepository.findAll(Sort.by("seatNumber")).stream()
                .map(s -> {
                    SeatStatus st = s.effectiveStatus(now);
                    return new SeatDto(s.getSeatNumber(), st.name(), st == SeatStatus.LOCKED ? s.getLockedUntil() : null);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationDto getReservation(UUID id) {
        Reservation r = reservationRepository.findById(id).orElseThrow(() -> notFound(id));
        return toDto(r, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getBookings() {
        return bookingRepository.findAllByOrderBySeatNumberAsc().stream()
                .map(b -> new BookingDto(b.getSeatNumber(), b.getReservationId(), b.getUserId(), b.getBookedAt()))
                .toList();
    }

    // ---------------------------------------------------------------- commands

    /** Temporarily locks the seats for the user (checkout start). */
    @Transactional
    public ReservationDto reserve(String userId, Collection<Integer> seatNumbers) {
        TreeSet<Integer> requested = new TreeSet<>(seatNumbers);
        if (requested.isEmpty()) {
            throw BookingException.of(HttpStatus.BAD_REQUEST, "NO_SEATS", "Select at least one seat.");
        }
        Instant now = clock.instant();

        List<Seat> seats = seatRepository.lockAllByNumbers(requested); // row locks, ascending order
        if (seats.size() != requested.size()) {
            throw BookingException.of(HttpStatus.BAD_REQUEST, "UNKNOWN_SEAT", "One or more seat numbers do not exist.");
        }

        List<Integer> unavailable = seats.stream()
                .filter(s -> !s.isAvailableAt(now))
                .map(Seat::getSeatNumber)
                .toList();
        if (!unavailable.isEmpty()) {
            throw BookingException.unavailable(unavailable);
        }

        Instant expiresAt = now.plus(lockDuration);
        Reservation reservation = reservationRepository.save(new Reservation(userId, now, expiresAt, requested));
        seats.forEach(s -> s.lock(reservation.getId(), expiresAt));
        return toDto(reservation, now);
    }

    /** Simulated payment: turns the user's active lock into permanent bookings. */
    @Transactional(noRollbackFor = BookingException.class) // keep the "expired" bookkeeping when we reject
    public ReservationDto confirm(UUID id, String userId) {
        Instant now = clock.instant();
        Reservation r = loadOwnedForUpdate(id, userId);

        if (r.getStatus() == ReservationStatus.CONFIRMED) {
            return toDto(r, now); // idempotent retry
        }
        if (r.getStatus() == ReservationStatus.CANCELLED) {
            throw BookingException.of(HttpStatus.CONFLICT, "RESERVATION_CANCELLED", "This reservation was cancelled.");
        }
        if (r.getStatus() == ReservationStatus.EXPIRED) {
            throw BookingException.expired();
        }
        if (!r.getExpiresAt().isAfter(now)) {
            releaseSeatsOf(r);
            r.setStatus(ReservationStatus.EXPIRED);
            throw BookingException.expired();
        }

        List<Seat> held = seatRepository.lockAllByNumbers(r.getSeatNumbers());
        boolean stillHeld = held.size() == r.getSeatNumbers().size() && held.stream()
                .allMatch(s -> s.getStatus() == SeatStatus.LOCKED && r.getId().equals(s.getReservationId()));
        if (!stillHeld) {
            r.setStatus(ReservationStatus.EXPIRED);
            throw BookingException.expired();
        }

        for (Seat s : held) {
            s.book();
            bookingRepository.save(new Booking(s.getSeatNumber(), r.getId(), r.getUserId(), now));
        }
        r.confirm(now);
        bookingRepository.flush(); // surface any UNIQUE violation inside this transaction
        return toDto(r, now);
    }

    /** User abandons checkout: releases the locked seats immediately. */
    @Transactional
    public ReservationDto cancel(UUID id, String userId) {
        Instant now = clock.instant();
        Reservation r = loadOwnedForUpdate(id, userId);

        switch (r.getStatus()) {
            case CANCELLED -> { return toDto(r, now); } // idempotent
            case CONFIRMED -> throw BookingException.of(HttpStatus.CONFLICT, "ALREADY_CONFIRMED",
                    "A confirmed booking cannot be cancelled.");
            case EXPIRED -> throw BookingException.expired();
            default -> {
                releaseSeatsOf(r);
                r.setStatus(ReservationStatus.CANCELLED);
            }
        }
        return toDto(r, now);
    }

    /** Called by the scheduler: frees locks that ran out. Reservations first, then seats (same order as confirm/cancel). */
    @Transactional
    public int expireStaleLocks() {
        Instant now = clock.instant();
        reservationRepository.expireStale(now);
        return seatRepository.releaseExpired(now);
    }

    // ---------------------------------------------------------------- helpers

    private Reservation loadOwnedForUpdate(UUID id, String userId) {
        Reservation r = reservationRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (!r.getUserId().equals(userId)) {
            throw BookingException.of(HttpStatus.FORBIDDEN, "NOT_RESERVATION_OWNER", "This reservation belongs to another user.");
        }
        return r;
    }

    private void releaseSeatsOf(Reservation r) {
        for (Seat s : seatRepository.lockAllByNumbers(r.getSeatNumbers())) {
            if (s.getStatus() == SeatStatus.LOCKED && r.getId().equals(s.getReservationId())) {
                s.release();
            }
        }
    }

    private static BookingException notFound(UUID id) {
        return BookingException.of(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation " + id + " does not exist.");
    }

    private ReservationDto toDto(Reservation r, Instant now) {
        boolean lapsed = r.getStatus() == ReservationStatus.ACTIVE && !r.getExpiresAt().isAfter(now);
        ReservationStatus shown = lapsed ? ReservationStatus.EXPIRED : r.getStatus();
        long remaining = shown == ReservationStatus.ACTIVE
                ? Math.max(0, (Duration.between(now, r.getExpiresAt()).toMillis() + 999) / 1000) : 0;
        return new ReservationDto(r.getId(), r.getUserId(), shown.name(),
                r.getSeatNumbers().stream().sorted().toList(), r.getExpiresAt(), remaining);
    }
}
