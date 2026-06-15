package com.hms.billing.service;

import com.hms.billing.dto.BillingRequest;
import com.hms.billing.dto.BillingResponse;
import com.hms.billing.dto.PaymentRequest;
import com.hms.common.enums.BillingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BillingService {

    BillingResponse generateBill(BillingRequest request);

    BillingResponse getBillById(Long id);

    BillingResponse getBillByNumber(String billNumber);

    BillingResponse getBillByAppointmentId(Long appointmentId);

    Page<BillingResponse> getBillsByStatus(BillingStatus status, Pageable pageable);

    Page<BillingResponse> getBillsByPatientId(Long patientId, Pageable pageable);

    BillingResponse processPayment(Long id, PaymentRequest request);
}
