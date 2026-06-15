package com.hms.appointment.service;

import com.hms.appointment.dto.AppointmentRequest;
import com.hms.appointment.dto.AppointmentResponse;
import com.hms.appointment.dto.RescheduleRequest;
import com.hms.common.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AppointmentService {

    AppointmentResponse bookAppointment(AppointmentRequest request);

    AppointmentResponse getAppointmentById(Long id);

    AppointmentResponse getAppointmentByNumber(String appointmentNumber);

    Page<AppointmentResponse> getAppointmentsByPatient(Long patientId, Pageable pageable);

    Page<AppointmentResponse> getAppointmentsByDoctor(Long doctorId, Pageable pageable);

    Page<AppointmentResponse> getAppointmentsByStatus(AppointmentStatus status, Pageable pageable);

    AppointmentResponse updateStatus(Long id, AppointmentStatus status);

    AppointmentResponse cancelAppointment(Long id, String reason);

    AppointmentResponse rescheduleAppointment(Long id, RescheduleRequest request);
}
