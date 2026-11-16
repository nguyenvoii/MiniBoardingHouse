package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.DepositRefundVoucher;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositRefundVoucherRepository extends JpaRepository<DepositRefundVoucher, Integer> {

    Optional<DepositRefundVoucher> findByCheckout_CheckoutId(Integer checkoutId);
}
