package com.hms.patient.service;

import com.hms.common.enums.Gender;
import com.hms.exception.BusinessValidationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.patient.dto.PatientRequest;
import com.hms.patient.dto.PatientResponse;
import com.hms.patient.entity.Patient;
import com.hms.patient.mapper.PatientMapper;
import com.hms.patient.repository.PatientRepository;
import com.hms.patient.service.impl.PatientServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService Unit Tests")
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @InjectMocks
    private PatientServiceImpl patientService;

    private PatientRequest patientRequest;
    private Patient patient;
    private PatientResponse patientResponse;

    @BeforeEach
    void setUp() {
        patientRequest = new PatientRequest();
        patientRequest.setFirstName("John");
        patientRequest.setLastName("Doe");
        patientRequest.setGender(Gender.MALE);
        patientRequest.setDateOfBirth(LocalDate.of(1990, 1, 1));
        patientRequest.setPhone("1234567890");
        patientRequest.setEmail("john.doe@example.com");

        patient = Patient.builder()
                .id(1L)
                .patientCode("PAT-202601-0001")
                .firstName("John")
                .lastName("Doe")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .phone("1234567890")
                .email("john.doe@example.com")
                .active(true)
                .build();

        patientResponse = new PatientResponse();
        patientResponse.setId(1L);
        patientResponse.setPatientCode("PAT-202601-0001");
        patientResponse.setFirstName("John");
        patientResponse.setLastName("Doe");
    }

    @Test
    @DisplayName("Should create patient successfully")
    void createPatient_Success() {
        when(patientRepository.existsByEmail(anyString())).thenReturn(false);
        when(patientRepository.existsByPhone(anyString())).thenReturn(false);
        when(patientMapper.toEntity(any(PatientRequest.class))).thenReturn(patient);
        when(patientRepository.count()).thenReturn(0L);
        when(patientRepository.save(any(Patient.class))).thenReturn(patient);
        when(patientMapper.toResponse(any(Patient.class))).thenReturn(patientResponse);

        PatientResponse result = patientService.createPatient(patientRequest);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("John");
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    @DisplayName("Should throw BusinessValidationException when email already exists")
    void createPatient_DuplicateEmail_ThrowsException() {
        when(patientRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> patientService.createPatient(patientRequest))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("already exists");

        verify(patientRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw BusinessValidationException when phone already exists")
    void createPatient_DuplicatePhone_ThrowsException() {
        when(patientRepository.existsByEmail(anyString())).thenReturn(false);
        when(patientRepository.existsByPhone(anyString())).thenReturn(true);

        assertThatThrownBy(() -> patientService.createPatient(patientRequest))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("already exists");

        verify(patientRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return patient by ID")
    void getPatientById_Success() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientMapper.toResponse(patient)).thenReturn(patientResponse);

        PatientResponse result = patientService.getPatientById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when patient not found")
    void getPatientById_NotFound_ThrowsException() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatientById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Patient");
    }

    @Test
    @DisplayName("Should deactivate patient successfully")
    void deactivatePatient_Success() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenReturn(patient);

        patientService.deactivatePatient(1L);

        assertThat(patient.isActive()).isFalse();
        verify(patientRepository).save(patient);
    }
}
