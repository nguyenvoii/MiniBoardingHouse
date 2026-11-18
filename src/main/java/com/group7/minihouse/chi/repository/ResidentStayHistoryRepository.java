package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.ResidentStayHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResidentStayHistoryRepository extends JpaRepository<ResidentStayHistory, Integer> {
    @EntityGraph(attributePaths = {"room", "tenant"})
    List<ResidentStayHistory> findByTenant_TenantIdOrderByStartDateDesc(Integer tenantId);
}
