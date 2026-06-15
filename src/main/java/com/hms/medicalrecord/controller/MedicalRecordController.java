package com.hms.medicalrecord.controller;

import com.hms.common.dto.ApiResponse;
import com.hms.medicalrecord.dto.MedicalRecordRequest;
import com.hms.medicalrecord.dto.MedicalRecordResponse;
import com.hms.medicalrecord.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/medical-records")
@RequiredArgsConstructor
@Tag(name = "Medical Records", description = "Medical record management APIs")
@SecurityRequirement(name = "bearerAuth")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @PostMapping
    @Operation(summary = "Create or update medical record")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<MedicalRecordResponse>> createOrUpdate(
            @Valid @RequestBody MedicalRecordRequest request) {
        MedicalRecordResponse response = medicalRecordService.createOrUpdateMedicalRecord(request);
        return ResponseEntity.ok(ApiResponse.success("Medical record saved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get medical record by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<MedicalRecordResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(medicalRecordService.getMedicalRecordById(id)));
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get medical record by patient ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<MedicalRecordResponse>> getByPatientId(@PathVariable Long patientId) {
        return ResponseEntity.ok(ApiResponse.success(medicalRecordService.getMedicalRecordByPatientId(patientId)));
    }
}
