package com.hms.prescription.mapper;

import com.hms.prescription.dto.PrescriptionItemDto;
import com.hms.prescription.dto.PrescriptionResponse;
import com.hms.prescription.entity.Prescription;
import com.hms.prescription.entity.PrescriptionItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PrescriptionMapper {

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "patientCode", source = "patient.patientCode")
    @Mapping(target = "patientName", expression = "java(prescription.getPatient().getFirstName() + \" \" + prescription.getPatient().getLastName())")
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorCode", source = "doctor.doctorCode")
    @Mapping(target = "doctorName", expression = "java(prescription.getDoctor().getFirstName() + \" \" + prescription.getDoctor().getLastName())")
    PrescriptionResponse toResponse(Prescription prescription);

    @Mapping(target = "id", source = "id")
    PrescriptionItemDto toItemDto(PrescriptionItem item);
}
