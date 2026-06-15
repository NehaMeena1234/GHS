package com.hms.billing.service;

import com.hms.appointment.entity.Appointment;
import com.hms.appointment.repository.AppointmentRepository;
import com.hms.billing.dto.BillingRequest;
import com.hms.billing.dto.BillingResponse;
import com.hms.billing.dto.PaymentRequest;
import com.hms.billing.entity.Billing;
import com.hms.billing.mapper.BillingMapper;
import com.hms.billing.repository.BillingRepository;
import com.hms.billing.service.impl.BillingServiceImpl;
import com.hms.common.enums.AppointmentStatus;
import com.hms.common.enums.BillingStatus;
import com.hms.common.enums.Gender;
import com.hms.doctor.entity.Doctor;
import com.hms.exception.BusinessValidationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.patient.entity.Patient;
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
@DisplayName("BillingService Unit Tests")
class BillingServiceTest {

    @Mock
    private BillingRepository billingRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BillingMapper billingMapper;

    @InjectMocks
    private BillingServiceImpl billingService;

    private Appointment appointment;
    private Billing billing;
    private BillingResponse billingResponse;

    @BeforeEach
    void setUp() {
        Doctor doctor = Doctor.builder()
                .id(1L)
                .doctorCode("DOC-CAR-2026-0001")
                .firstName("Jane")
                .lastName("Smith")
                .consultationFee(new BigDecimal("500.00"))
                .build();

        Patient patient = Patient.builder()
                .id(1L)
                .patientCode("PAT-202601-0001")
                .firstName("John")
                .lastName("Doe")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .phone("1234567890")
                .build();

        appointment = Appointment.builder()
                .id(1L)
                .appointmentNumber("APT-20260613-00001")
                .appointmentDate(LocalDate.now())
                .appointmentTime(LocalTime.of(10, 0))
                .status(AppointmentStatus.COMPLETED)
                .patient(patient)
                .doctor(doctor)
                .build();

        billing = Billing.builder()
                .id(1L)
                .billNumber("BILL-20260613-0001")
                .appointment(appointment)
                .consultationFee(new BigDecimal("500.00"))
                .additionalCharges(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("500.00"))
                .paidAmount(BigDecimal.ZERO)
                .status(BillingStatus.PENDING)
                .billDate(LocalDate.now())
                .build();

        billingResponse = new BillingResponse();
        billingResponse.setId(1L);
        billingResponse.setBillNumber("BILL-20260613-0001");
        billingResponse.setStatus(BillingStatus.PENDING);
        billingResponse.setTotalAmount(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Should generate bill successfully")
    void generateBill_Success() {
        BillingRequest request = new BillingRequest();
        request.setAppointmentId(1L);

        when(billingRepository.existsByAppointmentId(1L)).thenReturn(false);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        when(billingRepository.count()).thenReturn(0L);
        when(billingRepository.save(any(Billing.class))).thenReturn(billing);
        when(billingMapper.toResponse(any(Billing.class))).thenReturn(billingResponse);

        BillingResponse result = billingService.generateBill(request);

        assertThat(result).isNotNull();
        assertThat(result.getBillNumber()).isEqualTo("BILL-20260613-0001");
        verify(billingRepository).save(any(Billing.class));
    }

    @Test
    @DisplayName("Should throw BusinessValidationException when bill already exists for appointment")
    void generateBill_DuplicateBill_ThrowsException() {
        BillingRequest request = new BillingRequest();
        request.setAppointmentId(1L);

        when(billingRepository.existsByAppointmentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> billingService.generateBill(request))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("already exists");

        verify(billingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process full payment and mark as PAID")
    void processPayment_FullPayment_Success() {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new BigDecimal("500.00"));
        paymentRequest.setPaymentMethod("CASH");

        when(billingRepository.findById(1L)).thenReturn(Optional.of(billing));
        when(billingRepository.save(any(Billing.class))).thenReturn(billing);
        when(billingMapper.toResponse(any(Billing.class))).thenReturn(billingResponse);

        billingService.processPayment(1L, paymentRequest);

        assertThat(billing.getStatus()).isEqualTo(BillingStatus.PAID);
        assertThat(billing.getPaidAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Should process partial payment and mark as PARTIALLY_PAID")
    void processPayment_PartialPayment_Success() {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new BigDecimal("200.00"));
        paymentRequest.setPaymentMethod("CARD");

        when(billingRepository.findById(1L)).thenReturn(Optional.of(billing));
        when(billingRepository.save(any(Billing.class))).thenReturn(billing);
        when(billingMapper.toResponse(any(Billing.class))).thenReturn(billingResponse);

        billingService.processPayment(1L, paymentRequest);

        assertThat(billing.getStatus()).isEqualTo(BillingStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("Should throw BusinessValidationException when payment exceeds balance")
    void processPayment_ExceedsBalance_ThrowsException() {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new BigDecimal("1000.00"));
        paymentRequest.setPaymentMethod("CASH");

        when(billingRepository.findById(1L)).thenReturn(Optional.of(billing));

        assertThatThrownBy(() -> billingService.processPayment(1L, paymentRequest))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when bill not found")
    void getBillById_NotFound_ThrowsException() {
        when(billingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.getBillById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
