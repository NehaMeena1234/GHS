package com.hms.billing.dto;

import com.hms.common.enums.BillingStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class BillingResponse {

    private Long id;
    private String billNumber;
    private Long appointmentId;
    private String appointmentNumber;
    private Long patientId;
    private String patientName;
    private String doctorName;
    private BigDecimal consultationFee;
    private BigDecimal additionalCharges;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private BillingStatus status;
    private LocalDate billDate;
    private LocalDate paymentDate;
    private String paymentMethod;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
