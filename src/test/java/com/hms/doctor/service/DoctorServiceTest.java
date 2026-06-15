package com.hms.doctor.service;

import com.hms.doctor.dto.DoctorRequest;
import com.hms.doctor.dto.DoctorResponse;
import com.hms.doctor.entity.Doctor;
import com.hms.doctor.mapper.DoctorMapper;
import com.hms.doctor.repository.DoctorRepository;
import com.hms.doctor.service.impl.DoctorServiceImpl;
import com.hms.exception.BusinessValidationException;
import com.hms.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DoctorService Unit Tests")
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DoctorMapper doctorMapper;

    @InjectMocks
    private DoctorServiceImpl doctorService;

    private DoctorRequest doctorRequest;
    private Doctor doctor;
    private DoctorResponse doctorResponse;

    @BeforeEach
    void setUp() {
        doctorRequest = new DoctorRequest();
        doctorRequest.setFirstName("Jane");
        doctorRequest.setLastName("Smith");
        doctorRequest.setSpecialization("Cardiology");
        doctorRequest.setQualification("MBBS, MD");
        doctorRequest.setConsultationFee(new BigDecimal("500.00"));
        doctorRequest.setPhone("9876543210");
        doctorRequest.setEmail("jane.smith@hms.com");

        doctor = Doctor.builder()
                .id(1L)
                .doctorCode("DOC-CAR-2026-0001")
                .firstName("Jane")
                .lastName("Smith")
                .specialization("Cardiology")
                .qualification("MBBS, MD")
                .consultationFee(new BigDecimal("500.00"))
                .phone("9876543210")
                .email("jane.smith@hms.com")
                .available(true)
                .build();

        doctorResponse = new DoctorResponse();
        doctorResponse.setId(1L);
        doctorResponse.setDoctorCode("DOC-CAR-2026-0001");
        doctorResponse.setFirstName("Jane");
        doctorResponse.setLastName("Smith");
        doctorResponse.setAvailable(true);
    }

    @Test
    @DisplayName("Should create doctor successfully")
    void createDoctor_Success() {
        when(doctorRepository.existsByEmail(anyString())).thenReturn(false);
        when(doctorRepository.existsByPhone(anyString())).thenReturn(false);
        when(doctorMapper.toEntity(any(DoctorRequest.class))).thenReturn(doctor);
        when(doctorRepository.count()).thenReturn(0L);
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doctor);
        when(doctorMapper.toResponse(any(Doctor.class))).thenReturn(doctorResponse);

        DoctorResponse result = doctorService.createDoctor(doctorRequest);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("Jane");
        verify(doctorRepository).save(any(Doctor.class));
    }

    @Test
    @DisplayName("Should throw exception on duplicate email")
    void createDoctor_DuplicateEmail_ThrowsException() {
        when(doctorRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> doctorService.createDoctor(doctorRequest))
                .isInstanceOf(BusinessValidationException.class);

        verify(doctorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should get doctors by specialization")
    void getDoctorsBySpecialization_Success() {
        when(doctorRepository.findBySpecializationIgnoreCaseAndAvailableTrue("Cardiology"))
                .thenReturn(List.of(doctor));
        when(doctorMapper.toResponse(doctor)).thenReturn(doctorResponse);

        List<DoctorResponse> result = doctorService.getDoctorsBySpecialization("Cardiology");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("Should toggle doctor availability")
    void toggleAvailability_Success() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doctor);

        doctorService.toggleAvailability(1L);

        assertThat(doctor.isAvailable()).isFalse();
        verify(doctorRepository).save(doctor);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when doctor not found")
    void getDoctorById_NotFound_ThrowsException() {
        when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.getDoctorById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Doctor");
    }
}
