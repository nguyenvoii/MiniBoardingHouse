package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.StandardFurnishing;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandardFurnishingRepository extends JpaRepository<StandardFurnishing, Integer> {
    List<StandardFurnishing> findAllByOrderByItemNameAsc();
}
