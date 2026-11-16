package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.BankAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository extends JpaRepository<BankAccount, Integer> {

    /** Tai khoan dang dung cua 1 khu tro, mac dinh len dau. */
    @Query("select b from BankAccount b where b.property.propertyId = :propertyId and b.isActive = true "
            + "order by b.isDefault desc, b.bankAccountId")
    List<BankAccount> findActiveByProperty(@Param("propertyId") Integer propertyId);
}
