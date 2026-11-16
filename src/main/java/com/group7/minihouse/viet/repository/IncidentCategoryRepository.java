package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.IncidentCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentCategoryRepository extends JpaRepository<IncidentCategory, Integer> {

    @Query("select c from IncidentCategory c where (:type is null or c.categoryType = :type)"
            + " and (:active is null or c.isActive = :active) order by c.categoryType, c.categoryName")
    List<IncidentCategory> search(@Param("type") String type, @Param("active") Boolean active);

    Optional<IncidentCategory> findByCategoryCode(String categoryCode);

    @Query("select c from IncidentCategory c where c.categoryCode = :code and c.categoryType = :type and c.isActive = true")
    Optional<IncidentCategory> findActive(@Param("code") String code, @Param("type") String type);
}
