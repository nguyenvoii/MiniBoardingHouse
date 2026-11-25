package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.ContractTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Integer> {
    @org.springframework.data.jpa.repository.Query("select count(c) from RentalContract c where c.template.templateId = :id")
    long countContractsUsingTemplate(@org.springframework.data.repository.query.Param("id") Integer id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from ContractTemplate t where t.templateId = :id")
    java.util.Optional<ContractTemplate> findForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);

    List<ContractTemplate> findAllByOrderByTemplateIdDesc();
}
