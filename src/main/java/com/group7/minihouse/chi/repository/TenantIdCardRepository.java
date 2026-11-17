package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.TenantIdCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantIdCardRepository extends JpaRepository<TenantIdCard, Integer> {
    Optional<TenantIdCard> findByTenantTenantId(Integer tenantId);
}
