package com.hms.appointment.service.impl;

import com.hms.appointment.dto.AppointmentRequest;
import com.hms.appointment.dto.AppointmentResponse;
import com.hms.appointment.dto.RescheduleRequest;
import com.hms.appointment.entity.Appointment;
import com.hms.appointment.mapper.AppointmentMapper;
import com.hms.appointment.repository.AppointmentRepository;
import com.hms.appointment.service.AppointmentService;
import com.hms.common.enums.AppointmentStatus;
import com.hms.doctor.entity.Doctor;
import com.hms.doctor.service.DoctorService;
import com.hms.exception.AppointmentConflictException;
import com.hms.exception.BusinessValidationException;
import com.hms.exception.DoctorUnavailableException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.patient.entity.Patient;
import com.hms.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorService doctorService;
    private final AppointmentMapper appointmentMapper;

    private static final AtomicLong appointmentCounter = new AtomicLong(0);

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest request) {
        log.info("Booking appointment for patient {} with doctor {} on {} at {}",
                request.getPatientId(), request.getDoctorId(),
                request.getAppointmentDate(), request.getAppointmentTime());

        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));

        Doctor doctor = doctorService.findDoctorEntityById(request.getDoctorId());

        if (!doctor.isAvailable()) {
            throw new DoctorUnavailableException("Doctor " + doctor.getDoctorCode() + " is currently not available");
        }

        checkForConflict(doctor.getId(), request.getAppointmentDate(), request.getAppointmentTime(), null);

        Appointment appointment = Appointment.builder()
                .appointmentNumber(generateAppointmentNumber())
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .patient(patient)
                .doctor(doctor)
                .notes(request.getNotes())
                .status(AppointmentStatus.BOOKED)
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment booked: {} for patient {} with doctor {}",
                saved.getAppointmentNumber(), patient.getPatientCode(), doctor.getDoctorCode());
        return appointmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id) {
        return appointmentMapper.toResponse(findAppointmentById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentByNumber(String appointmentNumber) {
        Appointment appointment = appointmentRepository.findByAppointmentNumber(appointmentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "appointmentNumber", appointmentNumber));
        return appointmentMapper.toResponse(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAppointmentsByPatient(Long patientId, Pageable pageable) {
        return appointmentRepository.findByPatientId(patientId, pageable).map(appointmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAppointmentsByDoctor(Long doctorId, Pageable pageable) {
        return appointmentRepository.findByDoctorId(doctorId, pageable).map(appointmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAppointmentsByStatus(AppointmentStatus status, Pageable pageable) {
        return appointmentRepository.findByStatus(status, pageable).map(appointmentMapper::toResponse);
    }

    @Override
    @Transactional
    public AppointmentResponse updateStatus(Long id, AppointmentStatus status) {
        Appointment appointment = findAppointmentById(id);
        AppointmentStatus currentStatus = appointment.getStatus();

        validateStatusTransition(currentStatus, status);

        appointment.setStatus(status);
        Appointment updated = appointmentRepository.save(appointment);
        log.info("Appointment {} status updated from {} to {}", updated.getAppointmentNumber(), currentStatus, status);
        return appointmentMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public AppointmentResponse cancelAppointment(Long id, String reason) {
        Appointment appointment = findAppointmentById(id);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessValidationException("Appointment is already cancelled");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessValidationException("Cannot cancel a completed appointment");
        }

        LocalDateTime appointmentDateTime = LocalDateTime.of(
                appointment.getAppointmentDate(), appointment.getAppointmentTime());
        if (!appointmentDateTime.isAfter(LocalDateTime.now())) {
            throw new BusinessValidationException("Cannot cancel a past appointment");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(reason);
        Appointment cancelled = appointmentRepository.save(appointment);
        log.info("Appointment {} cancelled. Reason: {}", cancelled.getAppointmentNumber(), reason);
        return appointmentMapper.toResponse(cancelled);
    }

    @Override
    @Transactional
    public AppointmentResponse rescheduleAppointment(Long id, RescheduleRequest request) {
        Appointment appointment = findAppointmentById(id);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessValidationException("Cannot reschedule a " + appointment.getStatus().name().toLowerCase() + " appointment");
        }

        checkForConflict(appointment.getDoctor().getId(), request.getNewDate(), request.getNewTime(), id);

        LocalDate oldDate = appointment.getAppointmentDate();
        LocalTime oldTime = appointment.getAppointmentTime();

        appointment.setAppointmentDate(request.getNewDate());
        appointment.setAppointmentTime(request.getNewTime());
        appointment.setStatus(AppointmentStatus.BOOKED);

        Appointment rescheduled = appointmentRepository.save(appointment);
        log.info("Appointment {} rescheduled from {}/{} to {}/{}",
                rescheduled.getAppointmentNumber(), oldDate, oldTime,
                request.getNewDate(), request.getNewTime());
        return appointmentMapper.toResponse(rescheduled);
    }

    private void checkForConflict(Long doctorId, LocalDate date, LocalTime time, Long excludeId) {
        boolean conflict;
        if (excludeId == null) {
            conflict = appointmentRepository.findConflictingAppointment(doctorId, date, time).isPresent();
        } else {
            conflict = appointmentRepository.findConflictingAppointmentExcluding(doctorId, date, time, excludeId).isPresent();
        }
        if (conflict) {
            throw new AppointmentConflictException(
                    "Doctor already has an appointment on " + date + " at " + time);
        }
    }

    private void validateStatusTransition(AppointmentStatus current, AppointmentStatus next) {
        if (current == AppointmentStatus.CANCELLED || current == AppointmentStatus.COMPLETED) {
            throw new BusinessValidationException("Cannot change status from " + current.name());
        }
    }

    private Appointment findAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
    }

    private String generateAppointmentNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = appointmentRepository.count() + 1;
        return String.format("APT-%s-%05d", datePart, count);
    }
}
