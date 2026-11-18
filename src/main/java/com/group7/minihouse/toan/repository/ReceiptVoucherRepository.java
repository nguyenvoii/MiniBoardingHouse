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

    List<ReceiptVoucher> findByTransactionTransactionIdOrderByCreatedAtDesc(Integer transactionId);

    @Query("select coalesce(sum(v.amount), 0) from ReceiptVoucher v join v.transaction t "
         + "where v.createdAt >= :from and v.createdAt < :to and t.status = 'SUCCESS'")
    BigDecimal sumSuccessfulReceipts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

}
