package com.hms.medicalrecord.service;

import com.hms.medicalrecord.dto.MedicalRecordRequest;
import com.hms.medicalrecord.dto.MedicalRecordResponse;

public interface MedicalRecordService {

    MedicalRecordResponse createOrUpdateMedicalRecord(MedicalRecordRequest request);

    MedicalRecordResponse getMedicalRecordById(Long id);

    MedicalRecordResponse getMedicalRecordByPatientId(Long patientId);
}
