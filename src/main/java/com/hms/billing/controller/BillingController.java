package com.hms.billing.controller;

import com.hms.billing.dto.BillingRequest;
import com.hms.billing.dto.BillingResponse;
import com.hms.billing.dto.PaymentRequest;
import com.hms.billing.service.BillingService;
import com.hms.common.dto.ApiResponse;
import com.hms.common.enums.BillingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
@Tag(name = "Billing Management", description = "Billing and payment APIs")
@SecurityRequirement(name = "bearerAuth")
public class BillingController {

    private final BillingService billingService;

    @PostMapping
    @Operation(summary = "Generate a bill for an appointment")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<BillingResponse>> generateBill(@Valid @RequestBody BillingRequest request) {
        BillingResponse response = billingService.generateBill(request);
        log.info("Bill generated via API: {}", response.getBillNumber());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bill generated successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get bill by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<BillingResponse>> getBillById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getBillById(id)));
    }

    @GetMapping("/number/{billNumber}")
    @Operation(summary = "Get bill by bill number")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<BillingResponse>> getBillByNumber(@PathVariable String billNumber) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getBillByNumber(billNumber)));
    }

    @GetMapping("/appointment/{appointmentId}")
    @Operation(summary = "Get bill by appointment ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<BillingResponse>> getBillByAppointmentId(@PathVariable Long appointmentId) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getBillByAppointmentId(appointmentId)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get bills by status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<Page<BillingResponse>>> getBillsByStatus(
            @PathVariable BillingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getBillsByStatus(
                status, PageRequest.of(page, size, Sort.by("billDate").descending()))));
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get bills by patient")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<Page<BillingResponse>>> getBillsByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getBillsByPatientId(
                patientId, PageRequest.of(page, size, Sort.by("billDate").descending()))));
    }

    @PatchMapping("/{id}/payment")
    @Operation(summary = "Process payment for a bill")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<BillingResponse>> processPayment(
            @PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment processed", billingService.processPayment(id, request)));
    }
}
