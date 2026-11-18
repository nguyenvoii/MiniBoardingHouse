package com.group7.minihouse.toan.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.group7.minihouse.shared.entity.PaymentTransaction;

/** Truy vấn báo cáo giao dịch thu tiền theo ngày (UC127). */
public interface PaymentReportRepository extends JpaRepository<PaymentTransaction, Integer> {
    @Query(value = "select cast(pt.payment_date as date) as payment_day, u.user_id, u.full_name, "
            + "pt.payment_method, count(*) as transaction_count, sum(pt.amount_paid) as total_amount "
            + "from payment_transactions pt join users u on u.user_id = pt.received_by_user_id "
            + "where pt.payment_date >= :from and pt.payment_date < :to and pt.status = 'SUCCESS' "
            + "group by cast(pt.payment_date as date), u.user_id, u.full_name, pt.payment_method "
            + "order by payment_day desc, u.full_name", nativeQuery = true)
    List<Object[]> summarizeSuccessful(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select t.status, count(t), sum(t.amountPaid) from PaymentTransaction t "
         + "where t.paymentDate >= :from and t.paymentDate < :to group by t.status")
    List<Object[]> summarizeStatuses(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
