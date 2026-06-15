package com.hms.prescription.service.impl;

import com.hms.doctor.entity.Doctor;
import com.hms.doctor.service.DoctorService;
import com.hms.exception.ResourceNotFoundException;
import com.hms.patient.entity.Patient;
import com.hms.patient.repository.PatientRepository;
import com.hms.prescription.dto.PrescriptionItemDto;
import com.hms.prescription.dto.PrescriptionRequest;
import com.hms.prescription.dto.PrescriptionResponse;
import com.hms.prescription.entity.Prescription;
import com.hms.prescription.entity.PrescriptionItem;
import com.hms.prescription.mapper.PrescriptionMapper;
import com.hms.prescription.repository.PrescriptionRepository;
import com.hms.prescription.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final DoctorService doctorService;
    private final PrescriptionMapper prescriptionMapper;

    @Override
    @Transactional
    public PrescriptionResponse createPrescription(PrescriptionRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));

        Doctor doctor = doctorService.findDoctorEntityById(request.getDoctorId());

        Prescription prescription = Prescription.builder()
                .prescriptionNumber(generatePrescriptionNumber())
                .patient(patient)
                .doctor(doctor)
                .prescriptionDate(LocalDate.now())
                .diagnosis(request.getDiagnosis())
                .followUpDate(request.getFollowUpDate())
                .notes(request.getNotes())
                .build();

        List<PrescriptionItem> items = buildItems(request.getItems(), prescription);
        prescription.setItems(items);

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Prescription {} created for patient {} by doctor {}",
                saved.getPrescriptionNumber(), patient.getPatientCode(), doctor.getDoctorCode());
        return prescriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionResponse getPrescriptionById(Long id) {
        return prescriptionMapper.toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionResponse getPrescriptionByNumber(String prescriptionNumber) {
        Prescription prescription = prescriptionRepository.findByPrescriptionNumber(prescriptionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "prescriptionNumber", prescriptionNumber));
        return prescriptionMapper.toResponse(prescription);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrescriptionResponse> getPrescriptionsByPatient(Long patientId, Pageable pageable) {
        return prescriptionRepository.findByPatientId(patientId, pageable).map(prescriptionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrescriptionResponse> getPrescriptionsByDoctor(Long doctorId, Pageable pageable) {
        return prescriptionRepository.findByDoctorId(doctorId, pageable).map(prescriptionMapper::toResponse);
    }

    private Prescription findById(Long id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", id));
    }

    private List<PrescriptionItem> buildItems(List<PrescriptionItemDto> dtos, Prescription prescription) {
        return dtos.stream().map(dto -> PrescriptionItem.builder()
                .prescription(prescription)
                .medicineName(dto.getMedicineName())
                .dosage(dto.getDosage())
                .frequency(dto.getFrequency())
                .duration(dto.getDuration())
                .instructions(dto.getInstructions())
                .build()).collect(Collectors.toList());
    }

    private String generatePrescriptionNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = prescriptionRepository.count() + 1;
        return String.format("RX-%s-%04d", datePart, count);
    }
}
