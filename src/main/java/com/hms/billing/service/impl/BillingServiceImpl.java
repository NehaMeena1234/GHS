package com.hms.billing.service.impl;

import com.hms.appointment.entity.Appointment;
import com.hms.appointment.repository.AppointmentRepository;
import com.hms.billing.dto.BillingRequest;
import com.hms.billing.dto.BillingResponse;
import com.hms.billing.dto.PaymentRequest;
import com.hms.billing.entity.Billing;
import com.hms.billing.mapper.BillingMapper;
import com.hms.billing.repository.BillingRepository;
import com.hms.billing.service.BillingService;
import com.hms.common.enums.BillingStatus;
import com.hms.exception.BusinessValidationException;
import com.hms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final BillingRepository billingRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingMapper billingMapper;

    @Override
    @Transactional
    public BillingResponse generateBill(BillingRequest request) {
        log.info("Generating bill for appointment: {}", request.getAppointmentId());

        if (billingRepository.existsByAppointmentId(request.getAppointmentId())) {
            throw new BusinessValidationException("Bill already exists for appointment ID: " + request.getAppointmentId());
        }

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", request.getAppointmentId()));

        BigDecimal consultationFee = appointment.getDoctor().getConsultationFee();
        BigDecimal additionalCharges = request.getAdditionalCharges() != null ? request.getAdditionalCharges() : BigDecimal.ZERO;
        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;
        BigDecimal totalAmount = consultationFee.add(additionalCharges).subtract(discount);

        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessValidationException("Total amount cannot be negative");
        }

        Billing billing = Billing.builder()
                .billNumber(generateBillNumber())
                .appointment(appointment)
                .consultationFee(consultationFee)
                .additionalCharges(additionalCharges)
                .discount(discount)
                .totalAmount(totalAmount)
                .billDate(LocalDate.now())
                .notes(request.getNotes())
                .build();

        Billing saved = billingRepository.save(billing);
        log.info("Bill {} generated for appointment {}, total: {}",
                saved.getBillNumber(), appointment.getAppointmentNumber(), totalAmount);
        return billingMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingResponse getBillById(Long id) {
        return billingMapper.toResponse(findBillingById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public BillingResponse getBillByNumber(String billNumber) {
        Billing billing = billingRepository.findByBillNumber(billNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", "billNumber", billNumber));
        return billingMapper.toResponse(billing);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingResponse getBillByAppointmentId(Long appointmentId) {
        Billing billing = billingRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill for appointment", "appointmentId", appointmentId));
        return billingMapper.toResponse(billing);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BillingResponse> getBillsByStatus(BillingStatus status, Pageable pageable) {
        return billingRepository.findByStatus(status, pageable).map(billingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BillingResponse> getBillsByPatientId(Long patientId, Pageable pageable) {
        return billingRepository.findByAppointmentPatientId(patientId, pageable).map(billingMapper::toResponse);
    }

    @Override
    @Transactional
    public BillingResponse processPayment(Long id, PaymentRequest request) {
        Billing billing = findBillingById(id);

        if (billing.getStatus() == BillingStatus.PAID) {
            throw new BusinessValidationException("Bill is already fully paid");
        }
        if (billing.getStatus() == BillingStatus.CANCELLED) {
            throw new BusinessValidationException("Cannot process payment for a cancelled bill");
        }

        BigDecimal newPaidAmount = billing.getPaidAmount().add(request.getAmount());
        if (newPaidAmount.compareTo(billing.getTotalAmount()) > 0) {
            throw new BusinessValidationException("Payment amount exceeds outstanding balance");
        }

        billing.setPaidAmount(newPaidAmount);
        billing.setPaymentMethod(request.getPaymentMethod());
        billing.setPaymentDate(LocalDate.now());

        if (newPaidAmount.compareTo(billing.getTotalAmount()) == 0) {
            billing.setStatus(BillingStatus.PAID);
        } else {
            billing.setStatus(BillingStatus.PARTIALLY_PAID);
        }

        Billing updated = billingRepository.save(billing);
        log.info("Payment of {} processed for bill {}. Status: {}",
                request.getAmount(), billing.getBillNumber(), updated.getStatus());
        return billingMapper.toResponse(updated);
    }

    private Billing findBillingById(Long id) {
        return billingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", "id", id));
    }

    private String generateBillNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = billingRepository.count() + 1;
        return String.format("BILL-%s-%04d", datePart, count);
    }
}
