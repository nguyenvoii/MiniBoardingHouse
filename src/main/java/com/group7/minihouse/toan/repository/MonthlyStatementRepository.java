package com.group7.minihouse.toan.repository;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.ReceiptVoucher;

/** Truy vấn bảng kê hóa đơn và phiếu thu theo tháng (UC129). */
public interface MonthlyStatementRepository extends JpaRepository<Invoice, Integer> {
    @Query("select i from Invoice i join fetch i.tenant t join fetch i.calculatedFee f join fetch f.room r "
         + "where i.issueDate >= :from and i.issueDate < :to and i.paymentStatus <> 'CANCELLED' "
         + "order by i.issueDate, i.invoiceCode")
    List<Invoice> findInvoices(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select v from ReceiptVoucher v left join fetch v.transaction t left join fetch t.invoice i "
         + "where v.createdAt >= :from and v.createdAt < :to order by v.createdAt, v.voucherCode")
    List<ReceiptVoucher> findReceipts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
