package com.group7.minihouse.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.group7.minihouse.entity.ServiceRatePlan;

public interface ServiceRatePlanRepository extends JpaRepository<ServiceRatePlan, Integer> {

    List<ServiceRatePlan> findAllByOrderByEffectiveStartDateDesc();

    /** Cac bang gia dang con hieu luc tai mot ngay, moi nhat truoc. */
    @Query("select p from ServiceRatePlan p where p.effectiveStartDate <= :day "
            + "and (p.effectiveEndDate is null or p.effectiveEndDate >= :day) "
            + "order by p.effectiveStartDate desc")
    List<ServiceRatePlan> findEffectiveAt(@Param("day") LocalDate day);

    /** Bang gia dang mo (chua co ngay ket thuc) moi nhat. */
    Optional<ServiceRatePlan> findFirstByEffectiveEndDateIsNullOrderByEffectiveStartDateDesc();
}
