package com.example.trainbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.trainbooking.dto.Dtos.ReservationDto;
import com.example.trainbooking.dto.Dtos.SeatDto;
import com.example.trainbooking.exception.BookingException;
import com.example.trainbooking.service.BookingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

@SpringBootTest
class BookingFlowTest extends PostgresTestBase {

    @Autowired BookingService service;

    private String statusOf(int seat) {
        return service.getSeats().stream().filter(s -> s.seatNumber() == seat).map(SeatDto::status).findFirst().orElseThrow();
    }

    @Test
    void lockThenConfirm_marksSeatBooked() {
        ReservationDto r = service.reserve("alice", List.of(5, 6));
        assertThat(statusOf(5)).isEqualTo("LOCKED");
        assertThat(r.secondsRemaining()).isBetween(110L, 120L);

        service.confirm(r.reservationId(), "alice");

        assertThat(statusOf(5)).isEqualTo("BOOKED");
        assertThat(service.getBookings()).extracting("seatNumber").containsExactly(5, 6);
    }

    @Test
    void lockedSeatCannotBeReservedByAnotherUser() {
        service.reserve("alice", List.of(3));
        assertThatThrownBy(() -> service.reserve("bob", List.of(3, 4)))
                .isInstanceOfSatisfying(BookingException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getUnavailableSeats()).containsExactly(3);
                });
        assertThat(statusOf(4)).isEqualTo("AVAILABLE"); // all-or-nothing: seat 4 was not locked
    }

    @Test
    void bookedSeatCannotBeBookedAgain() {
        ReservationDto r = service.reserve("alice", List.of(7));
        service.confirm(r.reservationId(), "alice");
        assertThatThrownBy(() -> service.reserve("bob", List.of(7)))
                .isInstanceOfSatisfying(BookingException.class, e -> assertThat(e.getCode()).isEqualTo("SEAT_UNAVAILABLE"));
    }

    @Test
    void cancelReleasesSeat() {
        ReservationDto r = service.reserve("alice", List.of(9));
        service.cancel(r.reservationId(), "alice");
        assertThat(statusOf(9)).isEqualTo("AVAILABLE");
        assertThat(service.reserve("bob", List.of(9)).status()).isEqualTo("ACTIVE");
    }

    @Test
    void otherUsersCannotConfirmSomeoneElsesReservation() {
        ReservationDto r = service.reserve("alice", List.of(11));
        assertThatThrownBy(() -> service.confirm(r.reservationId(), "mallory"))
                .isInstanceOfSatisfying(BookingException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
