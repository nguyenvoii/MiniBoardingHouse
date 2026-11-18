package com.group7.minihouse.toan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import com.group7.minihouse.shared.entity.Invoice;

/** Cap nhat so tien da tra va trang thai thanh toan cua hoa don theo mot quy tac duy nhat. */
@Component
public class InvoiceBalanceCalculator {

    public BigDecimal paidOf(Invoice inv) {
        return inv.getPaidAmount() == null ? BigDecimal.ZERO : inv.getPaidAmount();
    }

    /** Con phai tra; hoa don da huy khong con khoan nao phai tra (0), nen khong nhan them tien. */
    public BigDecimal remainingOf(Invoice inv) {
        if ("CANCELLED".equals(inv.getPaymentStatus())) {
            return BigDecimal.ZERO;
        }
        return inv.getTotalAmount().subtract(paidOf(inv));
    }

    /** Dat so tien da tra va tinh lai trang thai (khong dong cham hoa don da huy). */
    public void setPaid(Invoice inv, BigDecimal paid, LocalDate today) {
        inv.setPaidAmount(paid);
        refreshStatus(inv, today);
    }

    public void refreshStatus(Invoice inv, LocalDate today) {
        if ("CANCELLED".equals(inv.getPaymentStatus())) {
            return;
        }
        BigDecimal paid = paidOf(inv);
        if (paid.compareTo(inv.getTotalAmount()) >= 0) {
            inv.setPaymentStatus("PAID");
        } else if (inv.getDueDate() != null && inv.getDueDate().isBefore(today)) {
            inv.setPaymentStatus("OVERDUE");
        } else if (paid.signum() > 0) {
            inv.setPaymentStatus("PARTIALLY_PAID");
        } else {
            inv.setPaymentStatus("UNPAID");
        }
    }
}
