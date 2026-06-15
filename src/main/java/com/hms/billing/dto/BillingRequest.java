package com.hms.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BillingRequest {

    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @DecimalMin(value = "0.0", message = "Additional charges cannot be negative")
    private BigDecimal additionalCharges;

    @DecimalMin(value = "0.0", message = "Discount cannot be negative")
    private BigDecimal discount;

    private String notes;
}
