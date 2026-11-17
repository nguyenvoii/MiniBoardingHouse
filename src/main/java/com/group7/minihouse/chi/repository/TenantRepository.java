package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Integer> {
    Optional<Tenant> findByPhoneNumber(String phoneNumber);
    Optional<Tenant> findByIdentityNumber(String identityNumber);
    List<Tenant> findByFullNameContainingIgnoreCaseOrPhoneNumberContainingOrIdentityNumberContaining(
            String name, String phone, String idCard);
}
