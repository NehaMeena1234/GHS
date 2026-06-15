package com.hms.doctor.service;

import com.hms.doctor.dto.DoctorRequest;
import com.hms.doctor.dto.DoctorResponse;
import com.hms.doctor.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DoctorService {

    DoctorResponse createDoctor(DoctorRequest request);

    DoctorResponse getDoctorById(Long id);

    DoctorResponse getDoctorByCode(String doctorCode);

    Page<DoctorResponse> getAllDoctors(Pageable pageable);

    Page<DoctorResponse> searchDoctors(String search, Pageable pageable);

    List<DoctorResponse> getDoctorsBySpecialization(String specialization);

    DoctorResponse updateDoctor(Long id, DoctorRequest request);

    void toggleAvailability(Long id);

    Doctor findDoctorEntityById(Long id);
}
