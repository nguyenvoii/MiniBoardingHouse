package com.group7.minihouse.toan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.PaymentTransaction;

/** Truy vấn giao dịch thanh toán của hóa đơn. */
public interface ToanPaymentTransactionRepository extends JpaRepository<PaymentTransaction, Integer> {

    boolean existsByInvoiceInvoiceIdAndStatus(Integer invoiceId, String status);

    /** Giao dich cho xac nhan (QR / cong thanh toan / chuyen khoan) duoc luu o trang thai FAILED. */
    boolean existsByInvoiceInvoiceIdAndStatusAndPaymentMethodIn(Integer invoiceId, String status,
                                                                java.util.Collection<String> methods);
}
