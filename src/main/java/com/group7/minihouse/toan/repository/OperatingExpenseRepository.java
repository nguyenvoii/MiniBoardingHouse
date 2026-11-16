package com.group7.minihouse.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.group7.minihouse.entity.OperatingExpense;

public interface OperatingExpenseRepository extends JpaRepository<OperatingExpense, Integer> {

    @Query("select coalesce(sum(e.amount), 0) from OperatingExpense e where e.property.propertyId = :propertyId "
            + "and e.expenseDate between :fromDate and :toDate")
    BigDecimal sumAmount(@Param("propertyId") Integer propertyId,
                         @Param("fromDate") LocalDate fromDate,
                         @Param("toDate") LocalDate toDate);
}
