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

    @org.springframework.beans.factory.annotation.Value("${app.payment.sepay.enabled:false}")
    private boolean sepayEnabled;

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
            throw bad("Cần invoiceId, method (CASH hoặc BANK_TRANSFER), amount");
        }
        String method = req.method().trim().toUpperCase();
        if (!STAFF_METHODS.contains(method)) {
            throw bad("method phải là CASH hoặc BANK_TRANSFER (QR/cổng điện tử dùng /api/payments/qr)");
        }
        Invoice inv = payableInvoice(req.invoiceId());
        BigDecimal amount = checkAmount(inv, req.amount());
        if ("BANK_TRANSFER".equals(method)) {
            // Khach da bam "tra bang QR" (giao dich cho) va chuyen dung so tien: xac nhan chinh giao dich do,
            // khong tao them mot giao dich thu hai de lai giao dich cho mo coi.
            for (PaymentTransaction pending : repo.findPendingOfInvoice(inv.getInvoiceId())) {
                if (pending.getAmountPaid().compareTo(amount) == 0) {
                    pending.setReceivedByUser(user(actorId));
                    ConfirmResult r = confirmWith(pending, amount, "Nhân viên đối chiếu chuyển khoản", false);
                    if (!r.confirmed()) {
                        throw new BusinessException(HttpStatus.CONFLICT, r.message());
                    }
                    audit(actorId, "RECORD_PAYMENT", "Doi chieu chuyen khoan " + amount + " vao giao dich "
                            + pending.getTransactionCode() + " cua hoa don " + inv.getInvoiceCode(), ip);
                    return r.payment();
                }
            }
        }
        PaymentTransaction t = newTransaction(inv, method, cleanCode(req.transactionCode()), amount, user(actorId));
        t.setStatus("SUCCESS");
        applyToInvoice(t, "Đã nhận " + method);
        audit(actorId, "RECORD_PAYMENT", "Ghi nhan thanh toan " + amount + " cho hoa don " + inv.getInvoiceCode(), ip);
        return toResponse(t);
    }

    // ------------------------------------------------------------------ UC86: QR / cong thanh toan

    /** Tao giao dich cho (FAILED) va tra thong tin tai khoan nhan tien + noi dung chuyen khoan = ma hoa don. */
    @Transactional
    public QrInfo createQr(QrRequest req, Integer receiverId, Integer actorId, String ip) {
        if (req == null || req.invoiceId() == null) {
            throw bad("Cần invoiceId");
        }
        String method = req.method() == null ? "QR_CODE" : req.method().trim().toUpperCase();
        if (!Set.of("QR_CODE", "GATEWAY").contains(method)) {
            throw bad("method phải là QR_CODE hoặc GATEWAY");
        }
        Invoice inv = payableInvoice(req.invoiceId());
        BigDecimal amount = req.amount() == null ? calculator.remainingOf(inv) : checkAmount(inv, req.amount());
        Integer propertyId = inv.getCalculatedFee().getBillingPeriod().getProperty().getPropertyId();
        BankAccountResponse bank = bankAccountService.list(propertyId).stream()
                .filter(BankAccountResponse::isDefault).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,
                        "Khu trọ chưa có tài khoản ngân hàng mặc định (UC119)"));
        // L13: da co giao dich cho cung hoa don, cung cach, cung so tien thi dung lai, khong sinh them giao dich cho moi lan bam
        for (PaymentTransaction pending : repo.findPendingOfInvoice(inv.getInvoiceId())) {
            if ("FAILED".equals(pending.getStatus()) && method.equals(pending.getPaymentMethod())
                    && pending.getAmountPaid().compareTo(amount) == 0) {
                return new QrInfo(toResponse(pending), bank.bankName(), bank.accountNumber(), bank.accountHolder(),
                        qrImage(bank, amount, inv.getInvoiceCode()), inv.getInvoiceCode());
            }
        }
        String code = (method.equals("QR_CODE") ? "QR-" : "GW-") + UUID.randomUUID();
        PaymentTransaction t = newTransaction(inv, method, code, amount, user(receiverId));
        t.setStatus("FAILED"); // cho xac nhan
        audit(actorId, "CREATE_QR_PAYMENT", "Tao giao dich " + code + " cho hoa don " + inv.getInvoiceCode(), ip);
        return new QrInfo(toResponse(t), bank.bankName(), bank.accountNumber(), bank.accountHolder(),
                qrImage(bank, amount, inv.getInvoiceCode()), inv.getInvoiceCode());
    }

    /**
     * Bat SePay: tra QR dong cua SePay (da nhung so tien + noi dung) de khach quet la chuyen dung, tien ve SePay bao ve.
     * Chua bat: dung anh QR tinh ma chu tro tai len. Ten ngan hang bo khoang trang ("MB Bank" -> "MBBank") cho khop
     * danh sach cua SePay (https://qr.sepay.vn/banks.json).
     */
    private String qrImage(BankAccountResponse bank, BigDecimal amount, String content) {
        if (!sepayEnabled || isBlank(bank.accountNumber()) || isBlank(bank.bankName())) {
            return bank.qrImageUrl();
        }
        return "https://qr.sepay.vn/img?acc=" + urlEncode(bank.accountNumber().trim())
                + "&bank=" + urlEncode(bank.bankName().replaceAll("\\s+", ""))
                + "&amount=" + amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
                + "&des=" + urlEncode(content);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Nguoi thue tu tao giao dich QR cho hoa don CUA MINH; nguoi tiep nhan ghi la chu tro dau tien con hoat dong. */
    @Transactional
    public QrInfo createQrForTenant(QrRequest req, Integer tenantId, Integer tenantUserId, String ip) {
        if (req == null || req.invoiceId() == null) {
            throw bad("Cần invoiceId");
        }
        Invoice inv = invoiceRepo.findById(req.invoiceId())
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn " + req.invoiceId()));
        if (!inv.getTenant().getTenantId().equals(tenantId)) {
            throw BusinessException.notFound("Hóa đơn " + req.invoiceId());
        }
        return createQr(req, firstActiveLandlordId(), tenantUserId, ip);
    }

    private Integer firstActiveLandlordId() {
        List<Integer> landlords = em.createQuery("select ur.user.userId from UserRole ur "
                + "where ur.role.roleCode = 'LANDLORD' and ur.user.status = 'ACTIVE' order by ur.user.userId",
                Integer.class).setMaxResults(1).getResultList();
        if (landlords.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Chưa có chủ trọ đang hoạt động để nhận thanh toán");
        }
        return landlords.get(0);
    }

    // ------------------------------------------------------------------ SePay: tien chuyen khoan ve tu dong

    /**
     * SePay bao co tien vao tai khoan: tim hoa don theo ma trong noi dung chuyen khoan va cong tien neu so tien khop.
     * Chi tu gach khi (a) co giao dich cho cung so tien (khach da bam tra bang QR) hoac (b) dung bang so con phai tra.
     * Cac truong hop con lai (lech tien, khong thay ma, hoa don da dong) chi ghi nhat ky de nhan vien xu ly, khong tu gach.
     * Moi giao dich SePay chi xu ly 1 lan: ma "SEPAY-{id}" luu o transaction_code, SePay goi lai cung id thi bo qua.
     */
    @Transactional
    public String confirmSepayTransfer(long sepayId, String content, BigDecimal amount, String ip) {
        String code = "SEPAY-" + sepayId;
        if (repo.existsByTransactionCode(code)) { // SePay goi lai: hoa don da PAID nen khong con khop duoc, bao ro la da xu ly
            return "Đã xử lý trước đó";
        }
        Integer receiver = firstActiveLandlordId();
        Invoice found = invoiceFromTransferContent(content);
        if (found == null) {
            audit(receiver, "SEPAY_UNMATCHED", code + ": " + amount + " VND, nội dung không có mã hóa đơn nào (hoặc nhiều mã): "
                    + abbreviate(content), ip);
            return "Không khớp hóa đơn";
        }
        Invoice inv = invoiceRepo.findByIdForUpdate(found.getInvoiceId())
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn " + found.getInvoiceId()));
        if (repo.existsByTransactionCode(code)) {
            return "Đã xử lý trước đó";
        }
        if ("PAID".equals(inv.getPaymentStatus()) || "CANCELLED".equals(inv.getPaymentStatus())) {
            audit(receiver, "SEPAY_UNMATCHED", code + ": " + amount + " VND vào hóa đơn " + inv.getInvoiceCode()
                    + " dang o trang thai " + inv.getPaymentStatus() + ", cần hoàn tiền cho khách", ip);
            return "Hóa đơn không còn chờ thanh toán";
        }
        for (PaymentTransaction pending : repo.findPendingOfInvoice(inv.getInvoiceId())) {
            if ("FAILED".equals(pending.getStatus()) && pending.getAmountPaid().compareTo(amount) == 0) {
                ConfirmResult r = confirmWith(pending, amount, "SePay xác nhận chuyển khoản", false);
                if (r.confirmed()) {
                    pending.setTransactionCode(code); // sau confirmWith (no em.refresh) de khong bi doc de
                }
                audit(receiver, "SEPAY_PAYMENT", code + ": " + r.message(), ip);
                return r.message();
            }
        }
        BigDecimal remaining = calculator.remainingOf(inv);
        if (amount.compareTo(remaining) == 0) {
            PaymentTransaction t = newTransaction(inv, "BANK_TRANSFER", code, amount, user(receiver));
            t.setStatus("SUCCESS");
            applyToInvoice(t, "SePay xác nhận chuyển khoản");
            audit(receiver, "SEPAY_PAYMENT", code + ": cộng " + amount + " VND vào hóa đơn " + inv.getInvoiceCode(), ip);
            return "Đã cộng vào hóa đơn " + inv.getInvoiceCode();
        }
        audit(receiver, "SEPAY_UNMATCHED", code + ": " + amount + " VND vào hóa đơn " + inv.getInvoiceCode()
                + " lệch số còn phải trả " + remaining + ", chờ nhân viên xử lý", ip);
        return "Lệch số tiền, chờ nhân viên xử lý";
    }

    /** Hoa don con phai tra co ma (bo dau gach) nam trong noi dung; nhieu ma thi lay ma dai nhat, bang nhau thi coi la mo ho. */
    private Invoice invoiceFromTransferContent(String content) {
        String flat = content == null ? "" : content.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (flat.isEmpty()) {
            return null;
        }
        List<Invoice> hits = repo.findPayableMentionedInFlat(flat);
        int longest = hits.stream().mapToInt(i -> flatCode(i).length()).max().orElse(0);
        List<Invoice> best = hits.stream().filter(i -> flatCode(i).length() == longest).toList();
        return best.size() == 1 ? best.get(0) : null;
    }

    private static String flatCode(Invoice i) {
        return i.getInvoiceCode().toLowerCase().replace("-", "");
    }

    private static String abbreviate(String s) {
        return s == null ? "" : s.length() <= 120 ? s : s.substring(0, 120);
    }

    /** Cong thanh toan goi ve: khop ma giao dich + so tien thi cong vao hoa don; lech tien thi giu nguyen cho nhan vien. */
    @Transactional
    public ConfirmResult callback(CallbackRequest req) {
        if (req == null || req.transactionCode() == null || req.amount() == null) {
            throw bad("Cần transactionCode và amount");
        }
        PaymentTransaction t = repo.findByTransactionCode(req.transactionCode().trim())
                .orElseThrow(() -> BusinessException.notFound("Giao dịch " + req.transactionCode()));
        return confirmWith(t, req.amount(), "Cổng thanh toán xác nhận", true);
    }

    @Transactional(readOnly = true)
    public PaymentResponse gatewayPaymentForTenant(Integer transactionId, Integer tenantId) {
        return toResponse(ownedGatewayPayment(transactionId, tenantId));
    }

    @Transactional
    public ConfirmResult reconcileGatewayPayment(Integer transactionId, Integer tenantId, BigDecimal settledAmount) {
        return confirmWith(ownedGatewayPayment(transactionId, tenantId), settledAmount, "payOS xác nhận", true);
    }

    private static final String GATEWAY_PREFIX = "PAYOS-";

    /**
     * Ma don payOS cho 1 lan thanh toan: 1e9 + id*1000 + lan thu. Moi lan bam "Thanh toán online" cho cung giao dich
     * dung ma don moi (payOS tu choi ma don trung); ma hien tai luu o transactionCode de doi soat sau do.
     */
    @Transactional
    public long nextGatewayOrder(Integer transactionId, Integer tenantId) {
        PaymentTransaction p = ownedGatewayPayment(transactionId, tenantId);
        long attempt = 1;
        String code = p.getTransactionCode();
        if (code != null && code.startsWith(GATEWAY_PREFIX)) {
            attempt = Long.parseLong(code.substring(GATEWAY_PREFIX.length())) % 1000 + 1;
        }
        if (attempt > 999) {
            throw new BusinessException(HttpStatus.CONFLICT, "Quá nhiều lần thử thanh toán, hãy tạo giao dịch mới");
        }
        long order = 1_000_000_000L + transactionId * 1000L + attempt;
        p.setTransactionCode(GATEWAY_PREFIX + order);
        return order;
    }

    @Transactional(readOnly = true)
    public long currentGatewayOrder(Integer transactionId, Integer tenantId) {
        String code = ownedGatewayPayment(transactionId, tenantId).getTransactionCode();
        if (code != null && code.startsWith(GATEWAY_PREFIX)) {
            return Long.parseLong(code.substring(GATEWAY_PREFIX.length()));
        }
        return 1_000_000_000L + transactionId; // giao dich tao truoc khi doi cach dat ma don
    }

    private PaymentTransaction ownedGatewayPayment(Integer transactionId, Integer tenantId) {
        PaymentTransaction payment = load(transactionId);
        if (!"GATEWAY".equals(payment.getPaymentMethod())
                || !tenantId.equals(payment.getInvoice().getTenant().getTenantId())) {
            throw BusinessException.notFound("Giao dịch cổng thanh toán");
        }
        return payment;
    }

    // ------------------------------------------------------------------ UC88: xac nhan chuyen khoan

    /** Nhan vien doi chieu sao ke: tim hoa don co ma nam trong noi dung chuyen khoan. */
    @Transactional(readOnly = true)
    public MatchResult match(String content) {
        if (content == null || content.isBlank()) {
            throw bad("Cần nội dung chuyển khoản");
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
            throw bad("Cần amountReceived (số tiền thực nhận trên sao kê)");
        }
        PaymentTransaction t = load(transactionId);
        ConfirmResult r = confirmWith(t, req.amountReceived(), "Nhân viên xác nhận chuyển khoản", false);
        audit(actorId, "CONFIRM_PAYMENT", "Xac nhan giao dich " + t.getTransactionCode() + ": " + r.message(), ip);
        return r;
    }

    /** Dong giao dich cho (khach chuyen sai so tien, bo, hoa don duoc thu cach khac): chuyen sang REVERSED, khong dong den tien. */
    @Transactional
    public PaymentResponse cancelPending(Integer transactionId, Integer actorId, String ip) {
        PaymentTransaction t = load(transactionId);
        invoiceRepo.findByIdForUpdate(t.getInvoice().getInvoiceId());
        em.refresh(t);
        if (!PENDING_METHODS.contains(t.getPaymentMethod()) || !"FAILED".equals(t.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Chỉ đóng được giao dịch đang chờ xác nhận");
        }
        t.setStatus("REVERSED");
        audit(actorId, "CANCEL_PENDING_PAYMENT", "Dong giao dich cho " + t.getTransactionCode() + " cua hoa don "
                + t.getInvoice().getInvoiceCode(), ip);
        return toResponse(t);
    }

    // ------------------------------------------------------------------ UC87: tra cuu

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(Integer invoiceId, String keyword, String status, int page, int size) {
        String st = status == null ? "" : status.trim().toUpperCase();
        if (!st.isEmpty() && !STATUSES.contains(st)) {
            throw bad("status phải là SUCCESS, FAILED hoặc REVERSED");
        }
        int s = size < 1 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(Math.min(Math.max(page, 0), 100_000), s,
                Sort.by(Sort.Direction.DESC, "paymentDate").and(Sort.by(Sort.Direction.DESC, "transactionId")));
        return new PageResponse<>(repo.search(invoiceId == null ? 0 : invoiceId, likePattern(keyword), st, pageable)
                .map(PaymentService::toResponse));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Integer id) {
        return toResponse(load(id));
    }

    // ------------------------------------------------------------------ noi bo

    private ConfirmResult confirmWith(PaymentTransaction t, BigDecimal received, String note, boolean replayAllowed) {
        if (!PENDING_METHODS.contains(t.getPaymentMethod())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Giao dịch tiền mặt không cần xác nhận");
        }
        // khoa hoa don roi doc lai giao dich: hai lan xac nhan dong thoi (callback + nhan vien) khong cong doi
        invoiceRepo.findByIdForUpdate(t.getInvoice().getInvoiceId());
        em.refresh(t);
        if (replayAllowed && "SUCCESS".equals(t.getStatus()) && received.compareTo(t.getAmountPaid()) == 0) {
            return new ConfirmResult(true, "Giao dịch đã được xác nhận", toResponse(t));
        }
        if (!"FAILED".equals(t.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Giao dịch đang ở trạng thái " + t.getStatus()
                    + ", chỉ xác nhận được giao dịch đang chờ");
        }
        String invStatus = t.getInvoice().getPaymentStatus();
        if ("CANCELLED".equals(invStatus) || "PAID".equals(invStatus)) {
            return new ConfirmResult(false, "Hóa đơn " + t.getInvoice().getInvoiceCode() + " dang o trang thai "
                    + invStatus + ", không cộng tiền vào được. Đóng giao dịch chờ và hoàn tiền cho khách nếu đã chuyển",
                    toResponse(t));
        }
        if (received.compareTo(t.getAmountPaid()) != 0) {
            return new ConfirmResult(false, "Số tiền thực nhận " + received + " khác số tiền giao dịch "
                    + t.getAmountPaid() + ", chưa cộng vào hóa đơn", toResponse(t));
        }
        Invoice inv = t.getInvoice();
        if (calculator.remainingOf(inv).compareTo(t.getAmountPaid()) < 0) {
            return new ConfirmResult(false, "Hóa đơn không còn đủ số phải trả (còn "
                    + calculator.remainingOf(inv) + "), có thể đã thanh toán bằng cách khác", toResponse(t));
        }
        t.setStatus("SUCCESS");
        // L6: giu ngay luc khach tao giao dich/chuyen tien (paymentDate luc tao), khong doi thanh gio xac nhan,
        // de doanh thu khong rot sang thang sau chi vi nhan vien xac nhan tre
        applyToInvoice(t, note);
        return new ConfirmResult(true, "Đã xác nhận và cộng vào hóa đơn " + inv.getInvoiceCode(), toResponse(t));
    }

    private void applyToInvoice(PaymentTransaction t, String note) {
        Invoice inv = t.getInvoice();
        calculator.setPaid(inv, calculator.paidOf(inv).add(t.getAmountPaid()), LocalDate.now(clock));
        if ("PAID".equals(inv.getPaymentStatus())) {
            for (PaymentTransaction other : repo.findPendingOfInvoice(inv.getInvoiceId())) {
                if (!"FAILED".equals(other.getStatus())) {
                    continue;
                }
                other.setStatus("REVERSED"); // hoa don da thu du: giao dich cho con lai khong con y nghia
            }
        }
        invoiceService.notifyPaymentConfirmed(inv, note + ": " + t.getAmountPaid() + " VND");
    }

    private PaymentTransaction newTransaction(Invoice inv, String method, String code, BigDecimal amount, User by) {
        if (code != null && repo.existsByTransactionCode(code)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Mã giao dịch " + code + " đã tồn tại");
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
        Invoice inv = invoiceRepo.findByIdForUpdate(id).orElseThrow(() -> BusinessException.notFound("Hóa đơn " + id));
        String st = inv.getPaymentStatus();
        if ("PAID".equals(st) || "CANCELLED".equals(st)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hóa đơn " + inv.getInvoiceCode() + " đang ở trạng thái "
                    + st);
        }
        return inv;
    }

    private BigDecimal checkAmount(Invoice inv, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw bad("Số tiền phải lớn hơn 0");
        }
        BigDecimal remaining = calculator.remainingOf(inv);
        if (amount.compareTo(remaining) > 0) {
            throw bad("Số tiền lớn hơn số còn phải trả (" + remaining
                    + "). Phần thừa xử lý ở chức năng thanh toán dư");
        }
        return amount;
    }

    private User user(Integer id) {
        if (id == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "Cần đăng nhập để ghi nhận người tiếp nhận (bật app.security.enabled=true và đăng nhập)");
        }
        return em.getReference(User.class, id);
    }

    private PaymentTransaction load(Integer id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("Giao dịch " + id));
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
            throw bad("Mã giao dịch tối đa 100 ký tự");
        }
        return c;
    }

    private static String likePattern(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        if (k.isEmpty()) {
            return "%";
        }
        return "%" + k.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[") + "%";
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
