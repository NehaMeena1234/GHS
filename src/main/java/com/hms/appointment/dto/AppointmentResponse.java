package com.hms.appointment.dto;

import com.hms.common.enums.AppointmentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
public class AppointmentResponse {

    private Long id;
    private String appointmentNumber;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private AppointmentStatus status;

    private Long patientId;
    private String patientCode;
    private String patientName;

    private Long doctorId;
    private String doctorCode;
    private String doctorName;
    private String specialization;

    private String notes;
    private String cancellationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
