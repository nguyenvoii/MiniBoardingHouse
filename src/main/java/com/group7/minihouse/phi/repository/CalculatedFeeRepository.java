package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.CalculatedMonthlyFee;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalculatedFeeRepository extends JpaRepository<CalculatedMonthlyFee, Integer> {

    @Query("select f from CalculatedMonthlyFee f where f.billingPeriod.billingPeriodId = :period "
            + "order by f.room.roomCode")
    List<CalculatedMonthlyFee> findByPeriod(@Param("period") Integer period);

    @Modifying
    @Query("delete from FeeItemDetail d where d.calculatedFee.billingPeriod.billingPeriodId = :period")
    void deleteItemsOfPeriod(@Param("period") Integer period);

    @Modifying
    @Query("delete from CalculatedMonthlyFee f where f.billingPeriod.billingPeriodId = :period")
    void deleteFeesOfPeriod(@Param("period") Integer period);

    /** Trung binh tong tien cac ky khac cua cung phong (null neu chua co ky nao khac). */
    @Query("select avg(f.totalCalculatedAmount) from CalculatedMonthlyFee f where f.room.roomId = :room "
            + "and f.billingPeriod.billingPeriodId <> :period")
    java.math.BigDecimal averageOfOtherPeriods(@Param("room") Integer room, @Param("period") Integer period);
}
