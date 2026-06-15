package com.hms.billing.mapper;

import com.hms.billing.dto.BillingResponse;
import com.hms.billing.entity.Billing;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BillingMapper {

    @Mapping(target = "appointmentId", source = "appointment.id")
    @Mapping(target = "appointmentNumber", source = "appointment.appointmentNumber")
    @Mapping(target = "patientId", source = "appointment.patient.id")
    @Mapping(target = "patientName", expression = "java(billing.getAppointment().getPatient().getFirstName() + \" \" + billing.getAppointment().getPatient().getLastName())")
    @Mapping(target = "doctorName", expression = "java(billing.getAppointment().getDoctor().getFirstName() + \" \" + billing.getAppointment().getDoctor().getLastName())")
    @Mapping(target = "balanceAmount", expression = "java(billing.getTotalAmount().subtract(billing.getPaidAmount()))")
    BillingResponse toResponse(Billing billing);
}
