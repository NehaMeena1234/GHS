package com.hms.medicalrecord.service.impl;

import com.hms.exception.ResourceNotFoundException;
import com.hms.medicalrecord.dto.MedicalRecordRequest;
import com.hms.medicalrecord.dto.MedicalRecordResponse;
import com.hms.medicalrecord.entity.MedicalRecord;
import com.hms.medicalrecord.mapper.MedicalRecordMapper;
import com.hms.medicalrecord.repository.MedicalRecordRepository;
import com.hms.medicalrecord.service.MedicalRecordService;
import com.hms.patient.entity.Patient;
import com.hms.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;
    private final MedicalRecordMapper medicalRecordMapper;

    @Override
    @Transactional
    public MedicalRecordResponse createOrUpdateMedicalRecord(MedicalRecordRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));

        MedicalRecord record = medicalRecordRepository.findByPatientId(request.getPatientId())
                .orElse(MedicalRecord.builder().patient(patient).build());

        record.setBloodPressure(request.getBloodPressure());
        record.setHeartRate(request.getHeartRate());
        record.setWeight(request.getWeight());
        record.setHeight(request.getHeight());
        record.setAllergies(request.getAllergies());
        record.setChronicConditions(request.getChronicConditions());
        record.setCurrentMedications(request.getCurrentMedications());
        record.setFamilyHistory(request.getFamilyHistory());
        record.setSurgicalHistory(request.getSurgicalHistory());
        record.setLastVisitDate(request.getLastVisitDate());
        record.setNotes(request.getNotes());

        MedicalRecord saved = medicalRecordRepository.save(record);
        log.info("Medical record saved for patient: {}", patient.getPatientCode());
        return medicalRecordMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalRecordResponse getMedicalRecordById(Long id) {
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "id", id));
        return medicalRecordMapper.toResponse(record);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalRecordResponse getMedicalRecordByPatientId(Long patientId) {
        MedicalRecord record = medicalRecordRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord for patient", "patientId", patientId));
        return medicalRecordMapper.toResponse(record);
    }
}
