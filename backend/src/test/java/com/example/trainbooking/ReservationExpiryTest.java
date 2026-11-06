package com.example.trainbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.trainbooking.dto.Dtos.ReservationDto;
import com.example.trainbooking.exception.BookingException;
import com.example.trainbooking.service.BookingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"app.lock-duration-seconds=1", "app.expiry-sweep-ms=200"})
class ReservationExpiryTest extends PostgresTestBase {

    @Autowired BookingService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void expiredLockCannotBeConfirmed_andSeatBecomesAvailable() throws Exception {
        ReservationDto r = service.reserve("alice", List.of(2));
        Thread.sleep(1500);

        assertThatThrownBy(() -> service.confirm(r.reservationId(), "alice"))
                .isInstanceOfSatisfying(BookingException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));

        assertThat(service.getSeats().get(1).status()).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from bookings", Integer.class)).isZero();
        // someone else can now take it
        assertThat(service.reserve("bob", List.of(2)).status()).isEqualTo("ACTIVE");
    }

    @Test
    void sweeperReleasesExpiredLocksInTheDatabase() throws Exception {
        service.reserve("alice", List.of(4));
        Thread.sleep(2000); // lock (1s) + a few sweeps (200ms)

        assertThat(jdbc.queryForObject("select status from seats where seat_number = 4", String.class)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from reservations where status = 'EXPIRED'", Integer.class)).isEqualTo(1);
    }
}
