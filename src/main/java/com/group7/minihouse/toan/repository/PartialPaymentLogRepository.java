package com.group7.minihouse.toan.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.PartialPaymentLog;

public interface PartialPaymentLogRepository extends JpaRepository<PartialPaymentLog, Integer> {

    List<PartialPaymentLog> findByInvoiceInvoiceIdOrderByInstallmentNumberAsc(Integer invoiceId);

    long countByInvoiceInvoiceId(Integer invoiceId);

    /** So dot lon nhat da ghi cua hoa don (0 neu chua co), de danh so dot tiep theo khong trung khi co dot bi xoa/sua. */
    @org.springframework.data.jpa.repository.Query(
            "select coalesce(max(l.installmentNumber), 0) from PartialPaymentLog l where l.invoice.invoiceId = :id")
    int maxInstallment(@org.springframework.data.repository.query.Param("id") Integer invoiceId);

    boolean existsByInvoiceInvoiceIdAndAmountGreaterThan(Integer invoiceId, java.math.BigDecimal amount);
}
