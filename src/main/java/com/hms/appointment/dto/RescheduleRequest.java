package com.hms.appointment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class RescheduleRequest {

    @NotNull(message = "New appointment date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDate newDate;

    @NotNull(message = "New appointment time is required")
    private LocalTime newTime;

    private String reason;
}
