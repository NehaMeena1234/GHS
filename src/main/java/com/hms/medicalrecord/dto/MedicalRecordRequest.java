package com.hms.medicalrecord.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class MedicalRecordRequest {

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    private String bloodPressure;

    @Positive(message = "Heart rate must be positive")
    private Integer heartRate;

    @Positive(message = "Weight must be positive")
    private BigDecimal weight;

    @Positive(message = "Height must be positive")
    private BigDecimal height;

    private String allergies;
    private String chronicConditions;
    private String currentMedications;
    private String familyHistory;
    private String surgicalHistory;
    private LocalDate lastVisitDate;
    private String notes;
}
