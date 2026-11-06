package com.example.trainbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Dtos {
    private Dtos() {}

    public record ReserveRequest(
            @NotBlank @Size(max = 64) String userId,
            @NotEmpty @Size(max = 20) List<@NotNull @Min(1) @Max(20) Integer> seatNumbers) {}

    public record UserRequest(@NotBlank @Size(max = 64) String userId) {}

    public record SeatDto(int seatNumber, String status, Instant lockedUntil) {}

    public record ReservationDto(UUID reservationId, String userId, String status,
                                 List<Integer> seatNumbers, Instant expiresAt, long secondsRemaining) {}

    public record BookingDto(int seatNumber, UUID reservationId, String userId, Instant bookedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String error, String message, List<Integer> unavailableSeats, Instant timestamp) {}
}
