package com.hms.prescription.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class PrescriptionResponse {

    private Long id;
    private String prescriptionNumber;
    private Long patientId;
    private String patientCode;
    private String patientName;
    private Long doctorId;
    private String doctorCode;
    private String doctorName;
    private LocalDate prescriptionDate;
    private String diagnosis;
    private LocalDate followUpDate;
    private String notes;
    private List<PrescriptionItemDto> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
