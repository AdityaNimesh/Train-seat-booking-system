package com.example.trainbooking.web;

import com.example.trainbooking.dto.Dtos.*;
import com.example.trainbooking.exception.BookingException;
import com.example.trainbooking.service.AdminService;
import com.example.trainbooking.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookingService;
    private final AdminService adminService;
    private final boolean resetEnabled;

    public BookingController(BookingService bookingService, AdminService adminService,
                             @Value("${app.demo-reset-enabled:false}") boolean resetEnabled) {
        this.bookingService = bookingService;
        this.adminService = adminService;
        this.resetEnabled = resetEnabled;
    }

    @GetMapping("/seats")
    public List<SeatDto> seats() {
        return bookingService.getSeats();
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationDto reserve(@Valid @RequestBody ReserveRequest request) {
        return bookingService.reserve(request.userId(), request.seatNumbers());
    }

    @GetMapping("/reservations/{id}")
    public ReservationDto reservation(@PathVariable UUID id) {
        return bookingService.getReservation(id);
    }

    @PostMapping("/reservations/{id}/confirm")
    public ReservationDto confirm(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return bookingService.confirm(id, request.userId());
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationDto cancel(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return bookingService.cancel(id, request.userId());
    }

    @GetMapping("/bookings")
    public List<BookingDto> bookings() {
        return bookingService.getBookings();
    }

    @PostMapping("/admin/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        if (!resetEnabled) {
            throw BookingException.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found.");
        }
        adminService.reset();
    }
}
