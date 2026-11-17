package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.DepositRefundVoucher;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepositRefundVoucherRepository extends JpaRepository<DepositRefundVoucher, Integer> {

    Optional<DepositRefundVoucher> findByCheckout_CheckoutId(Integer checkoutId);

    /**
     * UC132: phiếu của yêu cầu đã thanh lý có ngày trả thực tế trong khoảng, cộng phiếu còn chờ hoàn
     * (yêu cầu đang kiểm tra) có ngày trả dự kiến trong khoảng. Fetch sẵn phòng và khách để không bị N+1.
     */
    @Query("select v from DepositRefundVoucher v join fetch v.checkout c join fetch c.contract k"
            + " join fetch k.room join fetch k.tenant"
            + " where (c.status = 'FINALIZED' and c.actualCheckoutDate between :from and :to)"
            + " or (c.status = 'INSPECTING' and v.status = 'PENDING'"
            + " and c.expectedCheckoutDate between :from and :to)")
    List<DepositRefundVoucher> findForMonthReport(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
