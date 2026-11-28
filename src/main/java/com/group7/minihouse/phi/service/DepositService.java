package com.group7.minihouse.phi.service;

import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.util.DepositLedger;
import com.group7.minihouse.toan.dto.PaymentDtos.ReceiptRequest;
import com.group7.minihouse.toan.service.ReceiptService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thu tiền cọc của hợp đồng: lập phiếu thu loại DEPOSIT và theo dõi đã thu bao nhiêu, còn thiếu bao nhiêu. */
@Service
public class DepositService {

    public record DepositStatus(Integer contractId, String contractCode, BigDecimal required, BigDecimal collected,
                                BigDecimal remaining, boolean paidInFull) {
    }

    private static final Set<String> LIVE = Set.of("ACTIVE", "EXPIRING_SOON");

    private final EntityManager em;
    private final ReceiptService receipts;
    private final ActivityLogService activityLogService;

    public DepositService(EntityManager em, ReceiptService receipts, ActivityLogService activityLogService) {
        this.em = em;
        this.receipts = receipts;
        this.activityLogService = activityLogService;
    }

    @Transactional(readOnly = true)
    public DepositStatus status(Integer contractId) {
        return toStatus(load(contractId));
    }

    @Transactional
    public DepositStatus collect(Integer contractId, BigDecimal amount, Integer actorId, String ip) {
        RentalContract c = load(contractId);
        if (!LIVE.contains(c.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hợp đồng không còn hiệu lực, không thu cọc thêm được");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Số tiền cọc thu phải lớn hơn 0");
        }
        BigDecimal collected = DepositLedger.collected(em, c.getContractCode());
        BigDecimal required = c.getDepositAmount() == null ? BigDecimal.ZERO : c.getDepositAmount();
        if (collected.add(amount).compareTo(required) > 0) {
            throw new BusinessException("Thu vượt tiền cọc của hợp đồng: cần " + required.toBigInteger()
                    + ", đã thu " + collected.toBigInteger());
        }
        receipts.create(new ReceiptRequest(null, "DEPOSIT", amount,
                DepositLedger.note(c.getContractCode(), c.getRoom().getRoomCode())));
        if (actorId != null) {
            activityLogService.log(actorId, "COLLECT_DEPOSIT",
                    "Thu coc " + amount.toBigInteger() + " cho hop dong " + c.getContractCode(), ip);
        }
        return toStatus(c);
    }

    private RentalContract load(Integer id) {
        RentalContract c = em.find(RentalContract.class, id);
        if (c == null) {
            throw BusinessException.notFound("Hợp đồng " + id);
        }
        return c;
    }

    private DepositStatus toStatus(RentalContract c) {
        BigDecimal required = c.getDepositAmount() == null ? BigDecimal.ZERO : c.getDepositAmount();
        BigDecimal collected = DepositLedger.collected(em, c.getContractCode());
        return new DepositStatus(c.getContractId(), c.getContractCode(), required, collected,
                required.subtract(collected).max(BigDecimal.ZERO), collected.compareTo(required) >= 0);
    }
}
