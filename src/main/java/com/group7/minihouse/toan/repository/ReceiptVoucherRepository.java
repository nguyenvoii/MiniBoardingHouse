package com.group7.minihouse.toan.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.group7.minihouse.shared.entity.ReceiptVoucher;

public interface ReceiptVoucherRepository extends JpaRepository<ReceiptVoucher, Integer> {

    Optional<ReceiptVoucher> findByVoucherCode(String voucherCode);

    long countByVoucherCodeStartingWith(String prefix);

    boolean existsByVoucherCode(String voucherCode);

    /** Tong tien cac phieu thu da lap cho mot giao dich (de khong lap phieu vuot so tien giao dich). */
    @Query("select coalesce(sum(v.amount), 0) from ReceiptVoucher v where v.transaction.transactionId = :txId")
    BigDecimal sumByTransaction(@Param("txId") Integer transactionId);

    List<ReceiptVoucher> findByTransactionTransactionIdOrderByCreatedAtDesc(Integer transactionId);

    @Query("select v from ReceiptVoucher v join v.transaction t join t.invoice i"
            + " where i.tenant.tenantId = :tenantId and t.status = 'SUCCESS'"
            + " order by v.createdAt desc, v.voucherId desc")
    List<ReceiptVoucher> findSuccessfulForTenant(@Param("tenantId") Integer tenantId);

    @Query("select v from ReceiptVoucher v join v.transaction t join t.invoice i"
            + " where v.voucherId = :id and i.tenant.tenantId = :tenantId and t.status = 'SUCCESS'")
    Optional<ReceiptVoucher> findSuccessfulForTenant(@Param("tenantId") Integer tenantId, @Param("id") Integer id);

    @Query("select coalesce(sum(v.amount), 0) from ReceiptVoucher v join v.transaction t "
         + "where v.createdAt >= :from and v.createdAt < :to and t.status = 'SUCCESS'")
    BigDecimal sumSuccessfulReceipts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

}
