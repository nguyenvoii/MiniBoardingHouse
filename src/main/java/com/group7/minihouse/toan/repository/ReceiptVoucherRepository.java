package com.group7.minihouse.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.ReceiptVoucher;

public interface ReceiptVoucherRepository extends JpaRepository<ReceiptVoucher, Integer> {

    Optional<ReceiptVoucher> findByVoucherCode(String voucherCode);

    long countByVoucherCodeStartingWith(String prefix);

    List<ReceiptVoucher> findByTransactionTransactionIdOrderByCreatedAtDesc(Integer transactionId);

}
