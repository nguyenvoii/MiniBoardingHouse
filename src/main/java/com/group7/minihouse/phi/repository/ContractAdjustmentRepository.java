package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.ContractAdjustment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractAdjustmentRepository extends JpaRepository<ContractAdjustment, Integer> {

    List<ContractAdjustment> findByContractContractIdOrderByAdjustedAtDescAdjustmentIdDesc(Integer contractId);
}
