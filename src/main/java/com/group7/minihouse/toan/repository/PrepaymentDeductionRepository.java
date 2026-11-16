package com.group7.minihouse.toan.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.PrepaymentDeduction;

public interface PrepaymentDeductionRepository extends JpaRepository<PrepaymentDeduction, Integer> {

    List<PrepaymentDeduction> findByPrepaymentPrepaymentIdOrderByDeductedAtDesc(Integer prepaymentId);

    List<PrepaymentDeduction> findByTargetInvoiceInvoiceId(Integer invoiceId);
}
