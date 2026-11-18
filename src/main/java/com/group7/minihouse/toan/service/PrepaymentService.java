package com.group7.minihouse.toan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.PaymentDtos.*;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.PaymentTransaction;
import com.group7.minihouse.shared.entity.PrepaymentDeduction;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.TenantPrepayment;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserActivityLog;
import com.group7.minihouse.toan.repository.ToanUserRepository;
import com.group7.minihouse.toan.repository.ToanUserActivityLogRepository;
import com.group7.minihouse.toan.repository.InvoiceRepository;
import com.group7.minihouse.toan.repository.PrepaymentDeductionRepository;
import com.group7.minihouse.toan.repository.TenantPrepaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Xu ly thanh toan du / sai (UC 92): chuyen tien thua vao tai khoan du, can tru vao ky sau,
 * hoan tien, huy giao dich sai.
 *
 * Quy uoc: invoice.paid_amount la tong cac giao dich SUCCESS + cac dot thanh toan mot phan + can tru.
 */
@Service
public class PrepaymentService {

    private final TenantPrepaymentRepository prepaymentRepo;
    private final PrepaymentDeductionRepository deductionRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceBalanceCalculator calculator;
    private final ToanUserRepository userRepository;
    private final ToanUserActivityLogRepository activityLogRepository;
    private final ToanActivityWriter activity;

    @PersistenceContext
    private EntityManager em;

    public PrepaymentService(TenantPrepaymentRepository prepaymentRepo, PrepaymentDeductionRepository deductionRepo,
                             InvoiceRepository invoiceRepo, InvoiceBalanceCalculator calculator,
                             ToanUserRepository userRepository, ToanUserActivityLogRepository activityLogRepository,
                             ToanActivityWriter activity) {
        this.activity = activity;
        this.prepaymentRepo = prepaymentRepo;
        this.deductionRepo = deductionRepo;
        this.invoiceRepo = invoiceRepo;
        this.calculator = calculator;
        this.userRepository = userRepository;
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional(readOnly = true)
    public PrepaymentResponse getBalance(Integer tenantId) {
        return prepaymentRepo.findByTenantTenantId(tenantId).map(this::toResponse)
                .orElseGet(() -> new PrepaymentResponse(null, tenantId, BigDecimal.ZERO, null));
    }

    @Transactional(readOnly = true)
    public List<DeductionResponse> listDeductions(Integer tenantId) {
        return prepaymentRepo.findByTenantTenantId(tenantId)
                .map(p -> deductionRepo.findByPrepaymentPrepaymentIdOrderByDeductedAtDesc(p.getPrepaymentId())
                        .stream().map(this::toResponse).toList())
                .orElseGet(List::of);
    }

    /** Hoa don tra thua: phan vuot qua tong tien duoc chuyen sang tai khoan du cua khach. */
    @Transactional
    public PrepaymentResponse moveOverpaymentToBalance(Integer invoiceId) {
        return moveOverpaymentToBalance(invoiceId, null, null);
    }

    @Transactional
    public PrepaymentResponse moveOverpaymentToBalance(Integer invoiceId, Integer actorId, String ip) {
        Invoice inv = invoiceRepo.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> notFound("Hoa don " + invoiceId));
        if ("CANCELLED".equals(inv.getPaymentStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Không thể chuyển tiền dư từ hóa đơn đã hủy");
        }
        BigDecimal excess = calculator.paidOf(inv).subtract(inv.getTotalAmount());
        if (excess.signum() <= 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hoa don khong co tien tra thua");
        }
        TenantPrepayment pre = getOrCreate(inv.getTenant().getTenantId());
        pre.setExcessBalance(pre.getExcessBalance().add(excess));
        pre.setUpdatedAt(LocalDateTime.now());
        calculator.setPaid(inv, inv.getTotalAmount(), LocalDate.now());
        activity.log(actorId, "OVERPAYMENT_TO_BALANCE", "Chuyển tiền trả thừa " + excess + " của hóa đơn "
                + inv.getInvoiceCode() + " vào số dư khách", ip);
        return toResponse(pre);
    }

    /** Can tru tai khoan du vao mot hoa don (UC 92: can tru ky sau). */
    @Transactional
    public DeductionResponse deduct(DeductRequest req) {
        return deduct(req, null, null);
    }

    @Transactional
    public DeductionResponse deduct(DeductRequest req, Integer actorId, String ip) {
        if (req.tenantId() == null || req.targetInvoiceId() == null || req.amount() == null
                || req.amount().signum() <= 0) {
            throw bad("Can tenantId, targetInvoiceId, amount > 0");
        }
        Invoice inv = invoiceRepo.findByIdForUpdate(req.targetInvoiceId())
                .orElseThrow(() -> notFound("Hoa don " + req.targetInvoiceId()));
        if (!inv.getTenant().getTenantId().equals(req.tenantId())) {
            throw bad("Hoa don khong thuoc khach thue nay");
        }
        TenantPrepayment pre = prepaymentRepo.findByTenantIdForUpdate(req.tenantId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Khach khong co tien du"));
        DeductionResponse res = toResponse(applyDeduction(pre, inv, req.amount()));
        activity.log(actorId, "PREPAYMENT_DEDUCT", "Cấn trừ " + req.amount() + " từ số dư khách vào hóa đơn "
                + inv.getInvoiceCode(), ip);
        return res;
    }

    /** Tu dong can tru toan bo tien du (toi da bang so con no) vao hoa don moi phat hanh. */
    @Transactional
    public void autoDeduct(Invoice inv) {
        var pre = prepaymentRepo.findByTenantTenantId(inv.getTenant().getTenantId());
        if (pre.isEmpty() || pre.get().getExcessBalance().signum() <= 0) {
            return;
        }
        BigDecimal amount = pre.get().getExcessBalance().min(calculator.remainingOf(inv));
        if (amount.signum() > 0) {
            applyDeduction(pre.get(), inv, amount);
        }
    }

    /** Huy mot lan can tru: hoan lai so du, giam so da tra cua hoa don. */
    @Transactional
    public PrepaymentResponse undoDeduction(Integer deductionId) {
        return undoDeduction(deductionId, null, null);
    }

    @Transactional
    public PrepaymentResponse undoDeduction(Integer deductionId, Integer actorId, String ip) {
        PrepaymentDeduction d = deductionRepo.findById(deductionId)
                .orElseThrow(() -> notFound("Lan can tru " + deductionId));
        TenantPrepayment pre = prepaymentRepo.findByTenantIdForUpdate(d.getPrepayment().getTenant().getTenantId())
                .orElse(d.getPrepayment());
        Invoice inv = invoiceRepo.findByIdForUpdate(d.getTargetInvoice().getInvoiceId())
                .orElse(d.getTargetInvoice());
        pre.setExcessBalance(pre.getExcessBalance().add(d.getDeductedAmount()));
        pre.setUpdatedAt(LocalDateTime.now());
        calculator.setPaid(inv, calculator.paidOf(inv).subtract(d.getDeductedAmount()).max(BigDecimal.ZERO),
                LocalDate.now());
        deductionRepo.delete(d);
        activity.log(actorId, "PREPAYMENT_UNDO_DEDUCT", "Hủy cấn trừ " + d.getDeductedAmount() + " khỏi hóa đơn "
                + inv.getInvoiceCode(), ip);
        return toResponse(pre);
    }

    /** Hoan tien mat cho khach tu tai khoan du. */
    @Transactional
    public PrepaymentResponse refund(RefundRequest req) {
        return refund(req, null, null);
    }

    /** UC92: hoàn tiền từ số dư thừa; khóa số dư và ghi nhật ký người thực hiện trong cùng transaction. */
    @Transactional
    public PrepaymentResponse refund(RefundRequest req, Integer actorUserId, String ipAddress) {
        if (req.tenantId() == null || req.amount() == null || req.amount().signum() <= 0) {
            throw bad("Can tenantId, amount > 0");
        }
        TenantPrepayment pre = prepaymentRepo.findByTenantIdForUpdate(req.tenantId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Khach khong co tien du"));
        if (pre.getExcessBalance().compareTo(req.amount()) < 0) {
            throw bad("So tien hoan lon hon so du hien co (" + pre.getExcessBalance() + ")");
        }
        pre.setExcessBalance(pre.getExcessBalance().subtract(req.amount()));
        pre.setUpdatedAt(LocalDateTime.now());
        if (actorUserId != null) {
            User actor = userRepository.findById(actorUserId)
                    .orElseThrow(() -> com.group7.minihouse.shared.exception.BusinessException.notFound("Tài khoản người thao tác"));
            UserActivityLog log = new UserActivityLog();
            log.setUser(actor);
            log.setActionType("REFUND_PREPAYMENT");
            log.setDescription("Hoàn tiền dư cho khách thuê " + req.tenantId() + ": " + req.amount());
            log.setIpAddress(ipAddress);
            log.setCreatedAt(LocalDateTime.now());
            activityLogRepository.save(log);
        }
        return toResponse(pre);
    }

    /** Huy giao dich thanh toan sai: danh dau REVERSED va tru khoi so da tra cua hoa don. */
    @Transactional
    public void reverseTransaction(Integer transactionId) {
        reverseTransaction(transactionId, null, null);
    }

    @Transactional
    public void reverseTransaction(Integer transactionId, Integer actorUserId, String ipAddress) {
        PaymentTransaction t = em.find(PaymentTransaction.class, transactionId);
        if (t == null) {
            throw notFound("Giao dich " + transactionId);
        }
        if ("REVERSED".equals(t.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Giao dich da duoc huy truoc do");
        }
        if (!"SUCCESS".equals(t.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Chi huy duoc giao dich SUCCESS");
        }
        Invoice inv = invoiceRepo.findByIdForUpdate(t.getInvoice().getInvoiceId()).orElse(t.getInvoice());
        em.refresh(t); // doc lai trang thai sau khi da khoa hoa don, tranh dao hai lan cung luc
        if (!"SUCCESS".equals(t.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Giao dich da duoc huy truoc do");
        }
        t.setStatus("REVERSED");
        calculator.setPaid(inv, calculator.paidOf(inv).subtract(t.getAmountPaid()).max(BigDecimal.ZERO),
                LocalDate.now());
        if (actorUserId != null) {
            User actor = userRepository.findById(actorUserId)
                    .orElseThrow(() -> com.group7.minihouse.shared.exception.BusinessException.notFound("Tài khoản người thao tác"));
            UserActivityLog log = new UserActivityLog();
            log.setUser(actor);
            log.setActionType("REVERSE_PAYMENT");
            log.setDescription("Đảo giao dịch " + transactionId + " của hóa đơn " + inv.getInvoiceCode());
            log.setIpAddress(ipAddress);
            log.setCreatedAt(LocalDateTime.now());
            activityLogRepository.save(log);
        }
    }

    // ------------------------------------------------------------------ helpers

    private PrepaymentDeduction applyDeduction(TenantPrepayment pre, Invoice inv, BigDecimal amount) {
        if ("CANCELLED".equals(inv.getPaymentStatus()) || "PAID".equals(inv.getPaymentStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hoa don dang o trang thai " + inv.getPaymentStatus()
                    + ", khong can tru duoc");
        }
        if (pre.getExcessBalance().compareTo(amount) < 0) {
            throw bad("Tien du khong du (con " + pre.getExcessBalance() + ")");
        }
        if (amount.compareTo(calculator.remainingOf(inv)) > 0) {
            throw bad("So tien can tru lon hon so con phai tra cua hoa don");
        }
        pre.setExcessBalance(pre.getExcessBalance().subtract(amount));
        pre.setUpdatedAt(LocalDateTime.now());
        calculator.setPaid(inv, calculator.paidOf(inv).add(amount), LocalDate.now());
        PrepaymentDeduction d = new PrepaymentDeduction();
        d.setPrepayment(pre);
        d.setTargetInvoice(inv);
        d.setDeductedAmount(amount);
        d.setDeductedAt(LocalDateTime.now());
        return deductionRepo.save(d);
    }

    private TenantPrepayment getOrCreate(Integer tenantId) {
        return prepaymentRepo.findByTenantTenantId(tenantId).orElseGet(() -> {
            TenantPrepayment p = new TenantPrepayment();
            p.setTenant(em.getReference(Tenant.class, tenantId));
            p.setExcessBalance(BigDecimal.ZERO);
            p.setUpdatedAt(LocalDateTime.now());
            return prepaymentRepo.save(p);
        });
    }

    private Invoice findInvoice(Integer id) {
        return invoiceRepo.findById(id).orElseThrow(() -> notFound("Hoa don " + id));
    }

    private PrepaymentResponse toResponse(TenantPrepayment p) {
        return new PrepaymentResponse(p.getPrepaymentId(), p.getTenant().getTenantId(), p.getExcessBalance(),
                p.getUpdatedAt());
    }

    private DeductionResponse toResponse(PrepaymentDeduction d) {
        return new DeductionResponse(d.getDeductionId(), d.getPrepayment().getPrepaymentId(),
                d.getTargetInvoice().getInvoiceId(), d.getDeductedAmount(), d.getDeductedAt());
    }

    private static BusinessException bad(String msg) {
        return new BusinessException(HttpStatus.BAD_REQUEST, msg);
    }

    private static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, "Khong tim thay " + what);
    }
}
