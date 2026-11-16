package com.group7.minihouse.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.PartialPaymentLog;

public interface PartialPaymentLogRepository extends JpaRepository<PartialPaymentLog, Integer> {

    List<PartialPaymentLog> findByInvoiceInvoiceIdOrderByInstallmentNumberAsc(Integer invoiceId);

    long countByInvoiceInvoiceId(Integer invoiceId);
}
