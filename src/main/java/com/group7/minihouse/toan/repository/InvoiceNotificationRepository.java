package com.group7.minihouse.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.InvoiceNotification;

public interface InvoiceNotificationRepository extends JpaRepository<InvoiceNotification, Integer> {

    List<InvoiceNotification> findByInvoiceInvoiceIdOrderBySentAtDesc(Integer invoiceId);

    boolean existsByInvoiceInvoiceIdAndStatusAndSentAtAfter(
            Integer invoiceId, String status, LocalDateTime after);
}
