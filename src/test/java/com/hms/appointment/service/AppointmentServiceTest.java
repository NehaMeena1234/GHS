package com.hms.appointment.service;

import com.hms.appointment.dto.AppointmentRequest;
import com.hms.appointment.dto.AppointmentResponse;
import com.hms.appointment.dto.RescheduleRequest;
import com.hms.appointment.entity.Appointment;
import com.hms.appointment.mapper.AppointmentMapper;
import com.hms.appointment.repository.AppointmentRepository;
import com.hms.appointment.service.impl.AppointmentServiceImpl;
import com.hms.common.enums.AppointmentStatus;
import com.hms.common.enums.Gender;
import com.hms.doctor.entity.Doctor;
import com.hms.doctor.service.DoctorService;
import com.hms.exception.AppointmentConflictException;
import com.hms.exception.BusinessValidationException;
import com.hms.exception.DoctorUnavailableException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.patient.entity.Patient;
import com.hms.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService Unit Tests")
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorService doctorService;

    @Mock
    private AppointmentMapper appointmentMapper;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    private Patient patient;
    private Doctor doctor;
    private Appointment appointment;
    private AppointmentResponse appointmentResponse;
    private AppointmentRequest appointmentRequest;

    @BeforeEach
    void setUp() {
        patient = Patient.builder()
                .id(1L)
                .patientCode("PAT-202601-0001")
                .firstName("John")
                .lastName("Doe")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .phone("1234567890")
                .active(true)
                .build();

        doctor = Doctor.builder()
                .id(1L)
                .doctorCode("DOC-CAR-2026-0001")
                .firstName("Jane")
                .lastName("Smith")
                .specialization("Cardiology")
                .consultationFee(new BigDecimal("500.00"))
                .available(true)
                .build();

        appointment = Appointment.builder()
                .id(1L)
                .appointmentNumber("APT-20260613-00001")
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(10, 0))
                .status(AppointmentStatus.BOOKED)
                .patient(patient)
                .doctor(doctor)
                .build();

        appointmentResponse = new AppointmentResponse();
        appointmentResponse.setId(1L);
        appointmentResponse.setAppointmentNumber("APT-20260613-00001");
        appointmentResponse.setStatus(AppointmentStatus.BOOKED);

        appointmentRequest = new AppointmentRequest();
        appointmentRequest.setPatientId(1L);
        appointmentRequest.setDoctorId(1L);
        appointmentRequest.setAppointmentDate(LocalDate.now().plusDays(1));
        appointmentRequest.setAppointmentTime(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("Should book appointment successfully")
    void bookAppointment_Success() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(doctorService.findDoctorEntityById(1L)).thenReturn(doctor);
        when(appointmentRepository.findConflictingAppointment(any(), any(), any())).thenReturn(Optional.empty());
        when(appointmentRepository.count()).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(appointment);
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(appointmentResponse);

        AppointmentResponse result = appointmentService.bookAppointment(appointmentRequest);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("Should throw DoctorUnavailableException when doctor is not available")
    void bookAppointment_DoctorUnavailable_ThrowsException() {
        doctor.setAvailable(false);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(doctorService.findDoctorEntityById(1L)).thenReturn(doctor);

        assertThatThrownBy(() -> appointmentService.bookAppointment(appointmentRequest))
                .isInstanceOf(DoctorUnavailableException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw AppointmentConflictException on slot conflict")
    void bookAppointment_SlotConflict_ThrowsException() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(doctorService.findDoctorEntityById(1L)).thenReturn(doctor);
        when(appointmentRepository.findConflictingAppointment(any(), any(), any()))
                .thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.bookAppointment(appointmentRequest))
                .isInstanceOf(AppointmentConflictException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should cancel appointment successfully")
    void cancelAppointment_Success() {
        appointment.setAppointmentDate(LocalDate.now().plusDays(1));
        appointment.setAppointmentTime(LocalTime.of(10, 0));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(appointment);
        when(appointmentMapper.toResponse(any(Appointment.class))).thenReturn(appointmentResponse);

        appointmentService.cancelAppointment(1L, "Patient request");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(appointmentRepository).save(appointment);
    }

    @Test
    @DisplayName("Should throw BusinessValidationException when cancelling completed appointment")
    void cancelAppointment_AlreadyCompleted_ThrowsException() {
        appointment.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(1L, "reason"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("completed");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when appointment not found")
    void getAppointmentById_NotFound_ThrowsException() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.getAppointmentById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
