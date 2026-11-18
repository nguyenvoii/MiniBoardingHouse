package com.group7.minihouse.toan.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.PartialPaymentLog;

public interface PartialPaymentLogRepository extends JpaRepository<PartialPaymentLog, Integer> {

    List<PartialPaymentLog> findByInvoiceInvoiceIdOrderByInstallmentNumberAsc(Integer invoiceId);

    long countByInvoiceInvoiceId(Integer invoiceId);

    boolean existsByInvoiceInvoiceIdAndAmountGreaterThan(Integer invoiceId, java.math.BigDecimal amount);
}
