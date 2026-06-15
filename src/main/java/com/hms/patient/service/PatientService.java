package com.hms.patient.service;

import com.hms.patient.dto.PatientRequest;
import com.hms.patient.dto.PatientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PatientService {

    PatientResponse createPatient(PatientRequest request);

    PatientResponse getPatientById(Long id);

    PatientResponse getPatientByCode(String patientCode);

    Page<PatientResponse> getAllPatients(Pageable pageable);

    Page<PatientResponse> searchPatients(String search, Pageable pageable);

    PatientResponse updatePatient(Long id, PatientRequest request);

    void deactivatePatient(Long id);
}
