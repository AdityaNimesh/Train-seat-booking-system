package com.example.trainbooking.repository;

import com.example.trainbooking.domain.Reservation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Reservation r set r.status = com.example.trainbooking.domain.ReservationStatus.EXPIRED "
            + "where r.status = com.example.trainbooking.domain.ReservationStatus.ACTIVE and r.expiresAt <= :now")
    int expireStale(@Param("now") Instant now);
}
