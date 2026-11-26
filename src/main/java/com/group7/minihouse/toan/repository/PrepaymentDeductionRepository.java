package com.group7.minihouse.toan.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.group7.minihouse.shared.entity.PrepaymentDeduction;

public interface PrepaymentDeductionRepository extends JpaRepository<PrepaymentDeduction, Integer> {

    List<PrepaymentDeduction> findByPrepaymentPrepaymentIdOrderByDeductedAtDesc(Integer prepaymentId);

    List<PrepaymentDeduction> findByTargetInvoiceInvoiceId(Integer invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PrepaymentDeduction d where d.deductionId = :id")
    Optional<PrepaymentDeduction> findByIdForUpdate(@Param("id") Integer id);
}
