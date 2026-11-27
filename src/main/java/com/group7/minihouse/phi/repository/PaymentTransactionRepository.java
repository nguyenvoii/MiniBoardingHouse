package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Integer> {

    boolean existsByTransactionCode(String transactionCode);

    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    /** invoiceId = 0 la tat ca; kw la mau LIKE da escape (ky tu escape '\'); status '' la tat ca. */
    @Query("select t from PaymentTransaction t where (:invoiceId = 0 or t.invoice.invoiceId = :invoiceId) "
            + "and (:status = '' or t.status = :status) "
            + "and (lower(coalesce(t.transactionCode, '')) like :kw escape '\\' "
            + "or lower(t.invoice.invoiceCode) like :kw escape '\\')")
    Page<PaymentTransaction> search(@Param("invoiceId") Integer invoiceId, @Param("kw") String kw,
                                    @Param("status") String status, Pageable pageable);

    /** Giao dich dang cho xac nhan (luu o FAILED, kenh khong phai tien mat) cua mot hoa don. */
    @Query("select t from PaymentTransaction t where t.invoice.invoiceId = :invoiceId and t.status = 'FAILED' "
            + "and t.paymentMethod in ('QR_CODE', 'GATEWAY', 'BANK_TRANSFER') order by t.transactionId")
    List<PaymentTransaction> findPendingOfInvoice(@Param("invoiceId") Integer invoiceId);

    /** Hoa don con phai tra co ma nam trong noi dung chuyen khoan (de doi chieu UC88). */
    @Query("select i from Invoice i where i.paymentStatus in ('UNPAID', 'PARTIALLY_PAID', 'OVERDUE') "
            + "and lower(:content) like concat('%', lower(i.invoiceCode), '%')")
    List<com.group7.minihouse.shared.entity.Invoice> findPayableMentionedIn(@Param("content") String content);

    /**
     * Nhu tren nhung bo dau gach cua ma hoa don; content da bo moi ky tu khong phai chu/so va viet thuong.
     * Nhieu ngan hang xoa dau "-" va khoang trang khi chuyen noi dung, nen SePay bao ve "BA202611202" thay vi "BA202611-202".
     */
    @Query("select i from Invoice i where i.paymentStatus in ('UNPAID', 'PARTIALLY_PAID', 'OVERDUE') "
            + "and locate(replace(lower(i.invoiceCode), '-', ''), :content) > 0")
    List<com.group7.minihouse.shared.entity.Invoice> findPayableMentionedInFlat(@Param("content") String flatContent);
}
