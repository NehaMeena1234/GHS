package com.hms.medicalrecord.mapper;

import com.hms.medicalrecord.dto.MedicalRecordResponse;
import com.hms.medicalrecord.entity.MedicalRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MedicalRecordMapper {

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "patientCode", source = "patient.patientCode")
    @Mapping(target = "patientName", expression = "java(record.getPatient().getFirstName() + \" \" + record.getPatient().getLastName())")
    MedicalRecordResponse toResponse(MedicalRecord record);
}
