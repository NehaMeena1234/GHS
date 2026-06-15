package com.hms.patient.validator;

import com.hms.exception.BusinessValidationException;
import com.hms.patient.dto.PatientRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;

@Component
public class PatientValidator {

    private static final int MIN_AGE = 0;
    private static final int MAX_AGE = 150;

    public void validate(PatientRequest request) {
        validateAge(request.getDateOfBirth());
    }

    private void validateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) return;

        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new BusinessValidationException(
                    "Invalid date of birth. Age must be between " + MIN_AGE + " and " + MAX_AGE + " years");
        }
    }
}
