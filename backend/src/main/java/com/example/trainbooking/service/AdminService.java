package com.example.trainbooking.service;

import com.example.trainbooking.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Demo/testing helper: puts the coach back into its initial state (20 free seats, no history). */
@Service
public class AdminService {

    private final SeatRepository seats;
    private final BookingRepository bookings;
    private final ReservationRepository reservations;

    public AdminService(SeatRepository seats, BookingRepository bookings, ReservationRepository reservations) {
        this.seats = seats;
        this.bookings = bookings;
        this.reservations = reservations;
    }

    @Transactional
    public void reset() {
        seats.resetAll();
        bookings.deleteAllInBatch();
        reservations.deleteAllInBatch();
    }
}
