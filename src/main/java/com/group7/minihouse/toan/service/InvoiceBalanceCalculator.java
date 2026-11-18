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

    /**
     * M14/M16: dat lai tong hoa don MOT NOI duy nhat: them dong chenh lech vao fee_item_details (dong tay, tien to
     * "✎ " nhu BillingService) va cap nhat tong/giam tru/phu thu o bang tinh de hoa don in ra va bang tinh khong lech
     * nhau; trang thai di qua refreshStatus. Goi trong giao dich da khoa hoa don.
     */
    public void setTotalAndSync(jakarta.persistence.EntityManager em, Invoice inv, BigDecimal newTotal, String reason,
                                LocalDate today) {
        BigDecimal diff = newTotal.subtract(inv.getTotalAmount());
        var fee = inv.getCalculatedFee();
        if (diff.signum() != 0 && fee != null) {
            String name = "✎ Điều chỉnh: " + (reason == null ? "" : reason.trim());
            com.group7.minihouse.shared.entity.FeeItemDetail line = new com.group7.minihouse.shared.entity.FeeItemDetail();
            line.setCalculatedFee(fee);
            line.setItemName(name.length() > 90 ? name.substring(0, 90) : name);
            line.setQuantity(BigDecimal.ONE);
            line.setUnitPrice(diff);
            line.setSubtotal(diff.setScale(2, java.math.RoundingMode.HALF_UP));
            em.persist(line);
            BigDecimal oldFeeTotal = fee.getTotalCalculatedAmount() == null ? BigDecimal.ZERO : fee.getTotalCalculatedAmount();
            fee.setTotalCalculatedAmount(oldFeeTotal.add(diff));
            if (diff.signum() < 0) {
                BigDecimal d = fee.getDiscountAmount() == null ? BigDecimal.ZERO : fee.getDiscountAmount();
                fee.setDiscountAmount(d.add(diff.negate()));
            } else {
                BigDecimal s = fee.getSurchargeAmount() == null ? BigDecimal.ZERO : fee.getSurchargeAmount();
                fee.setSurchargeAmount(s.add(diff));
            }
        }
        inv.setTotalAmount(newTotal);
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
