package com.group7.minihouse.toan.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.group7.minihouse.shared.entity.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {

    Optional<Invoice> findByInvoiceCode(String invoiceCode);

    Optional<Invoice> findByShareLink(String shareLink);

    boolean existsByCalculatedFeeCalculatedFeeId(Integer calculatedFeeId);

    long countByInvoiceCodeStartingWith(String prefix);

    /** UC126: khóa hóa đơn trong lúc kiểm tra và hủy để tránh cập nhật đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.invoiceId = :invoiceId")
    Optional<Invoice> findByIdForUpdate(@Param("invoiceId") Integer invoiceId);

    List<Invoice> findByTenantTenantIdAndPaymentStatusInOrderByDueDateAsc(
            Integer tenantId, Collection<String> statuses);

    /** Hoa don chua thanh toan het co han thanh toan trong khoang [from, to] (nhac truoc han). */
    @Query("select i from Invoice i where i.paymentStatus in ('UNPAID', 'PARTIALLY_PAID') "
            + "and i.dueDate between :fromDate and :toDate")
    List<Invoice> findDueBetween(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** Hoa don chua thanh toan het da qua han. */
    @Query("select i from Invoice i where i.paymentStatus in ('UNPAID', 'PARTIALLY_PAID', 'OVERDUE') "
            + "and i.dueDate < :today")
    List<Invoice> findPastDue(@Param("today") LocalDate today);

    /** Tong hop theo thang cua mot khu (UC 76): [monthYear, soHoaDon, tongPhaiThu, daThu]. */
    @Query("select i.calculatedFee.billingPeriod.monthYear, count(i), sum(i.totalAmount), sum(i.paidAmount) "
            + "from Invoice i where i.calculatedFee.room.property.propertyId = :propertyId "
            + "and i.paymentStatus <> 'CANCELLED' "
            + "group by i.calculatedFee.billingPeriod.monthYear "
            + "order by i.calculatedFee.billingPeriod.monthYear desc")
    List<Object[]> summarizeByMonth(@Param("propertyId") Integer propertyId);

    /** Tong hop theo phong trong mot thang cua mot khu (UC 76). */
    @Query("select i from Invoice i where i.calculatedFee.room.property.propertyId = :propertyId "
            + "and i.calculatedFee.billingPeriod.monthYear = :monthYear "
            + "and i.paymentStatus <> 'CANCELLED' "
            + "order by i.calculatedFee.room.roomCode")
    List<Invoice> findByPropertyAndMonth(@Param("propertyId") Integer propertyId,
                                         @Param("monthYear") String monthYear);
}
