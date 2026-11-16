package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.CheckoutInspection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutInspectionRepository extends JpaRepository<CheckoutInspection, Integer> {

    Optional<CheckoutInspection> findByCheckout_CheckoutId(Integer checkoutId);
}
