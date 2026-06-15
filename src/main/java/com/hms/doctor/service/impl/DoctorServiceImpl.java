package com.hms.doctor.service.impl;

import com.hms.doctor.dto.DoctorRequest;
import com.hms.doctor.dto.DoctorResponse;
import com.hms.doctor.entity.Doctor;
import com.hms.doctor.mapper.DoctorMapper;
import com.hms.doctor.repository.DoctorRepository;
import com.hms.doctor.service.DoctorService;
import com.hms.exception.BusinessValidationException;
import com.hms.exception.ResourceNotFoundException;
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
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;

    @Override
    @Transactional
    public DoctorResponse createDoctor(DoctorRequest request) {
        log.info("Creating new doctor: {} {}", request.getFirstName(), request.getLastName());

        if (request.getEmail() != null && doctorRepository.existsByEmail(request.getEmail())) {
            throw new BusinessValidationException("Doctor with email '" + request.getEmail() + "' already exists");
        }
        if (doctorRepository.existsByPhone(request.getPhone())) {
            throw new BusinessValidationException("Doctor with phone '" + request.getPhone() + "' already exists");
        }

        Doctor doctor = doctorMapper.toEntity(request);
        doctor.setDoctorCode(generateDoctorCode(request.getSpecialization()));

        Doctor saved = doctorRepository.save(doctor);
        log.info("Doctor created with code: {} and assigned to: {}", saved.getDoctorCode(), saved.getSpecialization());
        return doctorMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorResponse getDoctorById(Long id) {
        return doctorMapper.toResponse(findDoctorEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorResponse getDoctorByCode(String doctorCode) {
        Doctor doctor = doctorRepository.findByDoctorCode(doctorCode)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "doctorCode", doctorCode));
        return doctorMapper.toResponse(doctor);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DoctorResponse> getAllDoctors(Pageable pageable) {
        return doctorRepository.findByAvailableTrue(pageable).map(doctorMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DoctorResponse> searchDoctors(String search, Pageable pageable) {
        return doctorRepository.searchDoctors(search, pageable).map(doctorMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorResponse> getDoctorsBySpecialization(String specialization) {
        return doctorRepository.findBySpecializationIgnoreCaseAndAvailableTrue(specialization)
                .stream()
                .map(doctorMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DoctorResponse updateDoctor(Long id, DoctorRequest request) {
        log.info("Updating doctor with id: {}", id);
        Doctor doctor = findDoctorEntityById(id);

        if (request.getEmail() != null && !request.getEmail().equals(doctor.getEmail())
                && doctorRepository.existsByEmail(request.getEmail())) {
            throw new BusinessValidationException("Email '" + request.getEmail() + "' is already in use");
        }
        if (!request.getPhone().equals(doctor.getPhone())
                && doctorRepository.existsByPhone(request.getPhone())) {
            throw new BusinessValidationException("Phone '" + request.getPhone() + "' is already in use");
        }

        doctorMapper.updateEntityFromRequest(request, doctor);
        Doctor updated = doctorRepository.save(doctor);
        log.info("Doctor updated: {}", updated.getDoctorCode());
        return doctorMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void toggleAvailability(Long id) {
        Doctor doctor = findDoctorEntityById(id);
        doctor.setAvailable(!doctor.isAvailable());
        doctorRepository.save(doctor);
        log.info("Doctor {} availability toggled to: {}", doctor.getDoctorCode(), doctor.isAvailable());
    }

    @Override
    @Transactional(readOnly = true)
    public Doctor findDoctorEntityById(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", id));
    }

    private String generateDoctorCode(String specialization) {
        String prefix = specialization.length() >= 3
                ? specialization.substring(0, 3).toUpperCase()
                : specialization.toUpperCase();
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
        long count = doctorRepository.count() + 1;
        return String.format("DOC-%s-%s-%04d", prefix, datePart, count);
    }
}
