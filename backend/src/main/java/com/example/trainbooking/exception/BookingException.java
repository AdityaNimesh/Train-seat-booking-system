package com.example.trainbooking.exception;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

public class BookingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<Integer> unavailableSeats;

    public BookingException(HttpStatus status, String code, String message, List<Integer> unavailableSeats) {
        super(message);
        this.status = status;
        this.code = code;
        this.unavailableSeats = unavailableSeats;
    }

    public static BookingException of(HttpStatus status, String code, String message) {
        return new BookingException(status, code, message, null);
    }

    public static BookingException unavailable(List<Integer> seats) {
        String list = seats.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return new BookingException(HttpStatus.CONFLICT, "SEAT_UNAVAILABLE",
                "Seat(s) " + list + " cannot be reserved: already locked by another user or booked.", seats);
    }

    public static BookingException expired() {
        return of(HttpStatus.GONE, "RESERVATION_EXPIRED",
                "This reservation has expired and the seats were released. Please select seats again.");
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public List<Integer> getUnavailableSeats() { return unavailableSeats; }
}
