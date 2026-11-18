package com.group7.minihouse.shared.util;

import com.group7.minihouse.shared.entity.ReceiptVoucher;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;

/**
 * Tiền cọc ĐÃ THU của một hợp đồng = tổng các phiếu thu loại DEPOSIT có ghi chú đúng mẫu
 * «Thu cọc hợp đồng MÃ phòng MÃ_PHÒNG». Không thêm cột vào DB: mẫu ghi chú là liên kết với hợp đồng.
 */
public final class DepositLedger {

    private DepositLedger() {
    }

    public static String note(String contractCode, String roomCode) {
        return "Thu cọc hợp đồng " + contractCode + " phòng " + roomCode;
    }

    public static BigDecimal collected(EntityManager em, String contractCode) {
        Object sum = em.createQuery("select coalesce(sum(v.amount), 0) from " + ReceiptVoucher.class.getSimpleName()
                        + " v where v.voucherType = 'DEPOSIT' and v.note like :p")
                .setParameter("p", "Thu cọc hợp đồng " + contractCode + " phòng %").getSingleResult();
        return sum instanceof BigDecimal b ? b : BigDecimal.ZERO;
    }
}
