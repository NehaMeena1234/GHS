package com.hms.medicalrecord.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class MedicalRecordResponse {

    private Long id;
    private Long patientId;
    private String patientCode;
    private String patientName;
    private String bloodPressure;
    private Integer heartRate;
    private BigDecimal weight;
    private BigDecimal height;
    private String allergies;
    private String chronicConditions;
    private String currentMedications;
    private String familyHistory;
    private String surgicalHistory;
    private LocalDate lastVisitDate;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
