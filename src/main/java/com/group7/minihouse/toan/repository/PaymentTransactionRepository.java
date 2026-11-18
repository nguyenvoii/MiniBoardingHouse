package com.group7.minihouse.toan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.PaymentTransaction;

/** Truy vấn giao dịch thanh toán của hóa đơn. */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Integer> {

    boolean existsByInvoiceInvoiceIdAndStatus(Integer invoiceId, String status);
}
