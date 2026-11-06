package com.example.trainbooking.repository;

import com.example.trainbooking.domain.Seat;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Integer> {

    /**
     * SELECT ... FOR UPDATE on the requested seats, always in ascending seat order.
     * Concurrent transactions queue up on the same rows and, because every transaction
     * acquires locks in the same order, they cannot deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.seatNumber in :numbers order by s.seatNumber")
    List<Seat> lockAllByNumbers(@Param("numbers") Collection<Integer> numbers);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Seat s set s.status = com.example.trainbooking.domain.SeatStatus.AVAILABLE, "
            + "s.reservationId = null, s.lockedUntil = null "
            + "where s.status = com.example.trainbooking.domain.SeatStatus.LOCKED and s.lockedUntil <= :now")
    int releaseExpired(@Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Seat s set s.status = com.example.trainbooking.domain.SeatStatus.AVAILABLE, "
            + "s.reservationId = null, s.lockedUntil = null")
    int resetAll();
}
