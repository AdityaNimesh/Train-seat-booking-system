package com.example.trainbooking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trainbooking.dto.Dtos.ReservationDto;
import com.example.trainbooking.exception.BookingException;
import com.example.trainbooking.service.BookingService;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ConcurrentBookingTest extends PostgresTestBase {

    @Autowired BookingService bookingService;
    @Autowired JdbcTemplate jdbc;

    record Attempt(boolean confirmed, List<Integer> seats) {}

    private Attempt reserveAndConfirm(String user, List<Integer> seats) {
        try {
            ReservationDto r = bookingService.reserve(user, seats);
            bookingService.confirm(r.reservationId(), user);
            return new Attempt(true, seats);
        } catch (BookingException e) {
            return new Attempt(false, seats);
        }
    }

    /** Releases all threads at the same instant to maximise contention. */
    private <T> List<T> runSimultaneously(int threads, IntFunction<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.apply(idx);
            }));
        }
        ready.await();
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) results.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        return results;
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void hundredUsersFightForTwentySeats_exactlyTwentyWin() throws Exception {
        // 100 users, each wants seat (i % 20) + 1 -> 5 contenders per seat
        List<Attempt> results = runSimultaneously(100, i -> reserveAndConfirm("user-" + i, List.of(i % 20 + 1)));

        assertThat(results.stream().filter(Attempt::confirmed).count()).isEqualTo(20);
        assertThat(count("select count(*) from bookings")).isEqualTo(20);
        assertThat(count("select count(distinct seat_number) from bookings")).isEqualTo(20);
        assertThat(count("select count(*) from seats where status = 'BOOKED'")).isEqualTo(20);
    }

    @Test
    void hundredUsersWantTheSameSeat_exactlyOneWins() throws Exception {
        List<Attempt> results = runSimultaneously(100, i -> reserveAndConfirm("user-" + i, List.of(1)));

        assertThat(results.stream().filter(Attempt::confirmed).count()).isEqualTo(1);
        assertThat(count("select count(*) from bookings")).isEqualTo(1);
    }

    @Test
    void overlappingMultiSeatRequests_neverDoubleBookOrExceedTwenty() throws Exception {
        List<Attempt> results = runSimultaneously(100, i -> {
            Random rnd = new Random(i);
            Set<Integer> picks = new TreeSet<>();
            int n = 1 + rnd.nextInt(3);
            while (picks.size() < n) picks.add(1 + rnd.nextInt(20));
            return reserveAndConfirm("user-" + i, new ArrayList<>(picks));
        });

        int seatsWon = results.stream().filter(Attempt::confirmed).mapToInt(a -> a.seats().size()).sum();
        int booked = count("select count(*) from bookings");
        assertThat(booked).isLessThanOrEqualTo(20);
        assertThat(count("select count(distinct seat_number) from bookings")).isEqualTo(booked); // no seat twice
        assertThat(seatsWon).isEqualTo(booked);                                                  // all-or-nothing per user
        assertThat(count("select count(*) from seats where status = 'LOCKED'")).isZero();       // nothing left dangling
    }
}
