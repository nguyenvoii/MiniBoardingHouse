package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.OperatingExpense;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperatingExpenseRepository extends JpaRepository<OperatingExpense, Integer> {

    /** from/to luon khac null (service dat can mac dinh) de tranh loi suy kieu tham so null tren SQL Server. */
    @Query("select e from OperatingExpense e where (:category is null or e.expenseCategory = :category)"
            + " and e.expenseDate >= :from and e.expenseDate <= :to")
    Page<OperatingExpense> search(@Param("category") String category, @Param("from") LocalDate from,
                                  @Param("to") LocalDate to, Pageable pageable);

    /** Moi dong: [category, tong tien, so khoan chi]. */
    @Query("select e.expenseCategory, sum(e.amount), count(e) from OperatingExpense e"
            + " where e.expenseDate >= :from and e.expenseDate <= :to"
            + " group by e.expenseCategory order by sum(e.amount) desc")
    List<Object[]> summarizeByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select distinct e.expenseCategory from OperatingExpense e order by e.expenseCategory")
    List<String> findDistinctCategories();
}
