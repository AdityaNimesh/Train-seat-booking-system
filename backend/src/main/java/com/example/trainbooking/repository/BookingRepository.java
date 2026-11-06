package com.example.trainbooking.repository;

import com.example.trainbooking.domain.Booking;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findAllByOrderBySeatNumberAsc();
}
