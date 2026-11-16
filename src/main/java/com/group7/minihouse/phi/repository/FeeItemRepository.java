package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.FeeItemDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeItemRepository extends JpaRepository<FeeItemDetail, Integer> {

    List<FeeItemDetail> findByCalculatedFeeCalculatedFeeIdOrderByDetailId(Integer calculatedFeeId);
}
