package com.hms.prescription.service;

import com.hms.prescription.dto.PrescriptionRequest;
import com.hms.prescription.dto.PrescriptionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PrescriptionService {

    PrescriptionResponse createPrescription(PrescriptionRequest request);

    PrescriptionResponse getPrescriptionById(Long id);

    PrescriptionResponse getPrescriptionByNumber(String prescriptionNumber);

    Page<PrescriptionResponse> getPrescriptionsByPatient(Long patientId, Pageable pageable);

    Page<PrescriptionResponse> getPrescriptionsByDoctor(Long doctorId, Pageable pageable);
}
