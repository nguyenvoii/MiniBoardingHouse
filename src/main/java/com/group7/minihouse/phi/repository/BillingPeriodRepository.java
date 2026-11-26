package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.MonthlyBillingPeriod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPeriodRepository extends JpaRepository<MonthlyBillingPeriod, Integer> {

    boolean existsByPropertyPropertyIdAndMonthYear(Integer propertyId, String monthYear);

    List<MonthlyBillingPeriod> findByPropertyPropertyIdOrderByMonthYearDesc(Integer propertyId);
    List<MonthlyBillingPeriod> findByStatusAndMonthYearOrderByBillingPeriodId(String status, String monthYear);
}
