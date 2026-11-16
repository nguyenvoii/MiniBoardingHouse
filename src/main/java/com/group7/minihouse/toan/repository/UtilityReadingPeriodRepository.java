package com.group7.minihouse.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.UtilityReadingPeriod;

public interface UtilityReadingPeriodRepository extends JpaRepository<UtilityReadingPeriod, Integer> {

    List<UtilityReadingPeriod> findByPropertyPropertyIdOrderByStartDateDesc(Integer propertyId);

    List<UtilityReadingPeriod> findByIsClosedAndEndDateBefore(Boolean isClosed, LocalDate date);

    boolean existsByPropertyPropertyIdAndPeriodName(Integer propertyId, String periodName);
}
