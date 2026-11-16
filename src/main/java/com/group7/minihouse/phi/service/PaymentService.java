package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.BankAccountResponse;
import com.group7.minihouse.phi.dto.PaymentDtos.CallbackRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.ConfirmRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.ConfirmResult;
import com.group7.minihouse.phi.dto.PaymentDtos.InvoiceMatch;
import com.group7.minihouse.phi.dto.PaymentDtos.MatchResult;
import com.group7.minihouse.phi.dto.PaymentDtos.PaymentRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.PaymentResponse;
import com.group7.minihouse.phi.dto.PaymentDtos.QrInfo;
import com.group7.minihouse.phi.dto.PaymentDtos.QrRequest;
import com.group7.minihouse.phi.repository.PaymentTransactionRepository;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.PaymentTransaction;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.repository.InvoiceRepository;
import com.group7.minihouse.toan.service.InvoiceBalanceCalculator;
import com.group7.minihouse.toan.service.InvoiceService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC85-88: thanh toan hoa don. Trang thai giao dich trong DB chi co SUCCESS, FAILED, REVERSED nen giao dich dang
 * cho xac nhan (QR/cong dien tu/chuyen khoan) duoc luu o FAILED va chi cong vao hoa don khi chuyen sang SUCCESS.
 * Tien thua (tra nhieu hon con no) khong xu ly o day: dung chuc nang thanh toan du cua Toan.
 * Cap nhat so da tra cua hoa don dung InvoiceBalanceCalculator cua Toan de moi cho tinh trang thai giong nhau.
 */
@Service
public class PaymentService {

    private static final Set<String> STAFF_METHODS = Set.of("CASH", "BANK_TRANSFER");
    private static final Set<String> PENDING_METHODS = Set.of("QR_CODE", "GATEWAY", "BANK_TRANSFER");
    private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED", "REVERSED");

    private final PaymentTransactionRepository repo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceBalanceCalculator calculator;
    private final InvoiceService invoiceService;
    private final BankAccountService bankAccountService;
    private final EntityManager em;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public PaymentService(PaymentTransactionRepository repo, InvoiceRepository invoiceRepo,
                          InvoiceBalanceCalculator calculator, InvoiceService invoiceService,
                          BankAccountService bankAccountService, EntityManager em,
                          ActivityLogService activityLogService, Clock clock) {
        this.repo = repo;
        this.invoiceRepo = invoiceRepo;
        this.calculator = calculator;
        this.invoiceService = invoiceService;
        this.bankAccountService = bankAccountService;
        this.em = em;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ UC85: tien mat / chuyen khoan

    /** Nhan vien da nhan tien: ghi giao dich SUCCESS va cong vao hoa don ngay. */
    @Transactional
    public PaymentResponse record(PaymentRequest req, Integer actorId, String ip) {
        if (req == null || req.invoiceId() == null || req.method() == null) {
            throw bad("Can invoiceId, method (CASH hoac BANK_TRANSFER), amount");
        }
        String method = req.method().trim().toUpperCase();
        if (!STAFF_METHODS.contains(method)) {
            throw bad("method phai la CASH hoac BANK_TRANSFER (QR/cong dien tu dung /api/payments/qr)");
        }
        Invoice inv = payableInvoice(req.invoiceId());
        BigDecimal amount = checkAmount(inv, req.amount());
        PaymentTransaction t = newTransaction(inv, method, cleanCode(req.transactionCode()), amount, user(actorId));
        t.setStatus("SUCCESS");
        applyToInvoice(t, "Da nhan " + method);
        audit(actorId, "RECORD_PAYMENT", "Ghi nhan thanh toan " + amount + " cho hoa don " + inv.getInvoiceCode(), ip);
        return toResponse(t);
    }

    // ------------------------------------------------------------------ UC86: QR / cong thanh toan

    /** Tao giao dich cho (FAILED) va tra thong tin tai khoan nhan tien + noi dung chuyen khoan = ma hoa don. */
    @Transactional
    public QrInfo createQr(QrRequest req, Integer receiverId, Integer actorId, String ip) {
        if (req == null || req.invoiceId() == null) {
            throw bad("Can invoiceId");
        }
        String method = req.method() == null ? "QR_CODE" : req.method().trim().toUpperCase();
        if (!Set.of("QR_CODE", "GATEWAY").contains(method)) {
            throw bad("method phai la QR_CODE hoac GATEWAY");
        }
        Invoice inv = payableInvoice(req.invoiceId());
        BigDecimal amount = req.amount() == null ? calculator.remainingOf(inv) : checkAmount(inv, req.amount());
        Integer propertyId = inv.getCalculatedFee().getBillingPeriod().getProperty().getPropertyId();
        BankAccountResponse bank = bankAccountService.list(propertyId).stream()
                .filter(BankAccountResponse::isDefault).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,
                        "Khu tro chua co tai khoan ngan hang mac dinh (UC119)"));
        String code = (method.equals("QR_CODE") ? "QR-" : "GW-") + UUID.randomUUID().toString().substring(0, 8)
                .toUpperCase();
        PaymentTransaction t = newTransaction(inv, method, code, amount, user(receiverId));
        t.setStatus("FAILED"); // cho xac nhan
        audit(actorId, "CREATE_QR_PAYMENT", "Tao giao dich " + code + " cho hoa don " + inv.getInvoiceCode(), ip);
        return new QrInfo(toResponse(t), bank.bankName(), bank.accountNumber(), bank.accountHolder(),
                bank.qrImageUrl(), inv.getInvoiceCode());
    }

    /** Nguoi thue tu tao giao dich QR cho hoa don CUA MINH; nguoi tiep nhan ghi la chu tro dau tien con hoat dong. */
    @Transactional
    public QrInfo createQrForTenant(QrRequest req, Integer tenantId, Integer tenantUserId, String ip) {
        if (req == null || req.invoiceId() == null) {
            throw bad("Can invoiceId");
        }
        Invoice inv = invoiceRepo.findById(req.invoiceId())
                .orElseThrow(() -> BusinessException.notFound("Hoa don " + req.invoiceId()));
        if (!inv.getTenant().getTenantId().equals(tenantId)) {
            throw BusinessException.notFound("Hoa don " + req.invoiceId());
        }
        List<Integer> landlords = em.createQuery("select ur.user.userId from UserRole ur "
                + "where ur.role.roleCode = 'LANDLORD' and ur.user.status = 'ACTIVE' order by ur.user.userId",
                Integer.class).setMaxResults(1).getResultList();
        if (landlords.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Chua co chu tro dang hoat dong de nhan thanh toan");
        }
        return createQr(req, landlords.get(0), tenantUserId, ip);
    }

    /** Cong thanh toan goi ve: khop ma giao dich + so tien thi cong vao hoa don; lech tien thi giu nguyen cho nhan vien. */
    @Transactional
    public ConfirmResult callback(CallbackRequest req) {
        if (req == null || req.transactionCode() == null || req.amount() == null) {
            throw bad("Can transactionCode va amount");
        }
        PaymentTransaction t = repo.findByTransactionCode(req.transactionCode().trim())
                .orElseThrow(() -> BusinessException.notFound("Giao dich " + req.transactionCode()));
        return confirmWith(t, req.amount(), "Cong thanh toan xac nhan");
    }

    // ------------------------------------------------------------------ UC88: xac nhan chuyen khoan

    /** Nhan vien doi chieu sao ke: tim hoa don co ma nam trong noi dung chuyen khoan. */
    @Transactional(readOnly = true)
    public MatchResult match(String content) {
        if (content == null || content.isBlank()) {
            throw bad("Can noi dung chuyen khoan");
        }
        List<InvoiceMatch> matches = repo.findPayableMentionedIn(content).stream()
                .filter(i -> mentions(content, i.getInvoiceCode()))
                .map(i -> new InvoiceMatch(i.getInvoiceId(), i.getInvoiceCode(), i.getTenant().getFullName(),
                        calculator.remainingOf(i), i.getPaymentStatus()))
                .toList();
        return new MatchResult(matches);
    }

    @Transactional
    public ConfirmResult confirm(Integer transactionId, ConfirmRequest req, Integer actorId, String ip) {
        if (req == null || req.amountReceived() == null) {
            throw bad("Can amountReceived (so tien thuc nhan tren sao ke)");
        }
        PaymentTransaction t = load(transactionId);
        ConfirmResult r = confirmWith(t, req.amountReceived(), "Nhan vien xac nhan chuyen khoan");
        audit(actorId, "CONFIRM_PAYMENT", "Xac nhan giao dich " + t.getTransactionCode() + ": " + r.message(), ip);
        return r;
    }

    // ------------------------------------------------------------------ UC87: tra cuu

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(Integer invoiceId, String keyword, String status, int page, int size) {
        String st = status == null ? "" : status.trim().toUpperCase();
        if (!st.isEmpty() && !STATUSES.contains(st)) {
            throw bad("status phai la SUCCESS, FAILED hoac REVERSED");
        }
        int s = size < 1 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), s,
                Sort.by(Sort.Direction.DESC, "paymentDate").and(Sort.by(Sort.Direction.DESC, "transactionId")));
        return new PageResponse<>(repo.search(invoiceId == null ? 0 : invoiceId, likePattern(keyword), st, pageable)
                .map(PaymentService::toResponse));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Integer id) {
        return toResponse(load(id));
    }

    // ------------------------------------------------------------------ noi bo

    private ConfirmResult confirmWith(PaymentTransaction t, BigDecimal received, String note) {
        if (!PENDING_METHODS.contains(t.getPaymentMethod())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Giao dich tien mat khong can xac nhan");
        }
        if (!"FAILED".equals(t.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Giao dich dang o trang thai " + t.getStatus()
                    + ", chi xac nhan duoc giao dich dang cho");
        }
        if (received.compareTo(t.getAmountPaid()) != 0) {
            return new ConfirmResult(false, "So tien thuc nhan " + received + " khac so tien giao dich "
                    + t.getAmountPaid() + ", chua cong vao hoa don", toResponse(t));
        }
        Invoice inv = t.getInvoice();
        if (calculator.remainingOf(inv).compareTo(t.getAmountPaid()) < 0) {
            return new ConfirmResult(false, "Hoa don khong con du so phai tra (con "
                    + calculator.remainingOf(inv) + "), co the da thanh toan bang cach khac", toResponse(t));
        }
        t.setStatus("SUCCESS");
        t.setPaymentDate(LocalDateTime.now(clock));
        applyToInvoice(t, note);
        return new ConfirmResult(true, "Da xac nhan va cong vao hoa don " + inv.getInvoiceCode(), toResponse(t));
    }

    private void applyToInvoice(PaymentTransaction t, String note) {
        Invoice inv = t.getInvoice();
        calculator.setPaid(inv, calculator.paidOf(inv).add(t.getAmountPaid()), LocalDate.now(clock));
        invoiceService.notifyPaymentConfirmed(inv, note + ": " + t.getAmountPaid() + " VND");
    }

    private PaymentTransaction newTransaction(Invoice inv, String method, String code, BigDecimal amount, User by) {
        if (code != null && repo.existsByTransactionCode(code)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ma giao dich " + code + " da ton tai");
        }
        PaymentTransaction t = new PaymentTransaction();
        t.setInvoice(inv);
        t.setPaymentMethod(method);
        t.setTransactionCode(code);
        t.setAmountPaid(amount);
        t.setPaymentDate(LocalDateTime.now(clock));
        t.setReceivedByUser(by);
        return repo.save(t);
    }

    private Invoice payableInvoice(Integer id) {
        Invoice inv = invoiceRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Hoa don " + id));
        String st = inv.getPaymentStatus();
        if ("PAID".equals(st) || "CANCELLED".equals(st)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hoa don " + inv.getInvoiceCode() + " dang o trang thai "
                    + st);
        }
        return inv;
    }

    private BigDecimal checkAmount(Invoice inv, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw bad("So tien phai lon hon 0");
        }
        BigDecimal remaining = calculator.remainingOf(inv);
        if (amount.compareTo(remaining) > 0) {
            throw bad("So tien lon hon so con phai tra (" + remaining
                    + "). Phan thua xu ly o chuc nang thanh toan du");
        }
        return amount;
    }

    private User user(Integer id) {
        if (id == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "Can dang nhap de ghi nhan nguoi tiep nhan (bat app.security.enabled=true va dang nhap)");
        }
        return em.getReference(User.class, id);
    }

    private PaymentTransaction load(Integer id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("Giao dich " + id));
    }

    static boolean mentions(String content, String invoiceCode) {
        return Pattern.compile("(?<![A-Za-z0-9-])" + Pattern.quote(invoiceCode) + "(?![A-Za-z0-9-])",
                Pattern.CASE_INSENSITIVE).matcher(content).find();
    }

    private static String cleanCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.trim();
        if (c.length() > 100) {
            throw bad("Ma giao dich toi da 100 ky tu");
        }
        return c;
    }

    private static String likePattern(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        if (k.isEmpty()) {
            return "%";
        }
        return "%" + k.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private void audit(Integer actorId, String action, String description, String ip) {
        if (actorId != null) {
            activityLogService.log(actorId, action, description, ip);
        }
    }

    private static BusinessException bad(String m) {
        return new BusinessException(HttpStatus.BAD_REQUEST, m);
    }

    static PaymentResponse toResponse(PaymentTransaction t) {
        User u = t.getReceivedByUser();
        return new PaymentResponse(t.getTransactionId(), t.getInvoice().getInvoiceId(),
                t.getInvoice().getInvoiceCode(), t.getPaymentMethod(), t.getTransactionCode(), t.getAmountPaid(),
                t.getPaymentDate(), u == null ? null : u.getUserId(), u == null ? null : u.getFullName(),
                t.getStatus());
    }
}
