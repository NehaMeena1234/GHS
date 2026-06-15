package com.hms.doctor.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class DoctorResponse {

    private Long id;
    private String doctorCode;
    private String firstName;
    private String lastName;
    private String fullName;
    private String specialization;
    private String qualification;
    private BigDecimal consultationFee;
    private String phone;
    private String email;
    private boolean available;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
