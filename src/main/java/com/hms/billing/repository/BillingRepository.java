package com.hms.billing.repository;

import com.hms.billing.entity.Billing;
import com.hms.common.enums.BillingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingRepository extends JpaRepository<Billing, Long> {

    Optional<Billing> findByBillNumber(String billNumber);

    Optional<Billing> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    Page<Billing> findByStatus(BillingStatus status, Pageable pageable);

    Page<Billing> findByAppointmentPatientId(Long patientId, Pageable pageable);
}
