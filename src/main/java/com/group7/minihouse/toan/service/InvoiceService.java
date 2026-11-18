package com.group7.minihouse.toan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.group7.minihouse.toan.dto.InvoiceDtos.*;
import com.group7.minihouse.shared.entity.CalculatedMonthlyFee;
import com.group7.minihouse.shared.entity.FeeItemDetail;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.InvoiceNotification;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserActivityLog;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.CancelInvoiceRequest;
import com.group7.minihouse.toan.dto.CancelInvoiceResponse;
import com.group7.minihouse.toan.repository.PaymentTransactionRepository;
import com.group7.minihouse.toan.repository.PartialPaymentLogRepository;
import com.group7.minihouse.toan.repository.UserActivityLogRepository;
import com.group7.minihouse.toan.repository.UserRepository;
import com.group7.minihouse.toan.repository.InvoiceNotificationRepository;
import com.group7.minihouse.toan.repository.InvoiceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Hoa don (UC 76, 78-84): phat hanh, xem chi tiet / in, gui email & website, nhac han / qua han,
 * thong bao khi dieu chinh, tra cuu, tong hop tien phai thu.
 */
@Service
public class InvoiceService {

    public static final String SHARE_PREFIX = "/api/invoices/share/";
    static final String ST_REMINDER = "REMINDER_BEFORE_DUE";
    static final String ST_OVERDUE = "OVERDUE_NOTICE";
    static final String ST_ADJUSTED = "INVOICE_ADJUSTED";

    private final InvoiceRepository invoiceRepo;
    private final InvoiceNotificationRepository notificationRepo;
    private final InvoiceBalanceCalculator calculator;
    private final PrepaymentService prepaymentService;
    private final ObjectProvider<InvoiceMailGateway> mailGateway;
    private final PaymentTransactionRepository paymentTransactionRepo;
    private final PartialPaymentLogRepository partialPaymentLogRepo;
    private final UserActivityLogRepository activityLogRepo;
    private final UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    public InvoiceService(InvoiceRepository invoiceRepo, InvoiceNotificationRepository notificationRepo,
                          InvoiceBalanceCalculator calculator, PrepaymentService prepaymentService,
                          ObjectProvider<InvoiceMailGateway> mailGateway,
                          PaymentTransactionRepository paymentTransactionRepo,
                          PartialPaymentLogRepository partialPaymentLogRepo,
                          UserActivityLogRepository activityLogRepo, UserRepository userRepo) {
        this.invoiceRepo = invoiceRepo;
        this.notificationRepo = notificationRepo;
        this.calculator = calculator;
        this.prepaymentService = prepaymentService;
        this.mailGateway = mailGateway;
        this.paymentTransactionRepo = paymentTransactionRepo;
        this.partialPaymentLogRepo = partialPaymentLogRepo;
        this.activityLogRepo = activityLogRepo;
        this.userRepo = userRepo;
    }

    // ------------------------------------------------------------------ UC 79: phat hanh

    @Transactional
    public InvoiceResponse issue(IssueRequest req) {
        if (req.calculatedFeeId() == null) {
            throw bad("Thieu calculatedFeeId");
        }
        return toResponse(issueOne(req.calculatedFeeId(), req.issueDate(), req.dueDate()));
    }

    /** Phat hanh hang loat cho moi phong cua mot ky tinh tien chua co hoa don. */
    @Transactional
    public List<InvoiceResponse> issueBatch(IssueBatchRequest req) {
        if (req.billingPeriodId() == null) {
            throw bad("Thieu billingPeriodId");
        }
        List<Integer> feeIds = em.createQuery(
                "select f.calculatedFeeId from CalculatedMonthlyFee f "
                        + "where f.billingPeriod.billingPeriodId = :bp "
                        + "and not exists (select i.invoiceId from Invoice i where i.calculatedFee = f) "
                        + "order by f.room.roomCode", Integer.class)
                .setParameter("bp", req.billingPeriodId()).getResultList();
        List<InvoiceResponse> out = new ArrayList<>();
        for (Integer id : feeIds) {
            out.add(toResponse(issueOne(id, req.issueDate(), req.dueDate())));
        }
        return out;
    }

    private Invoice issueOne(Integer feeId, LocalDate issueDate, LocalDate dueDate) {
        CalculatedMonthlyFee fee = em.find(CalculatedMonthlyFee.class, feeId);
        if (fee == null) {
            throw notFound("Bang tinh tien " + feeId);
        }
        if ("DRAFT".equals(fee.getBillingPeriod().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ky tinh tien chua duoc duyet (dang DRAFT)");
        }
        if (invoiceRepo.existsByCalculatedFeeCalculatedFeeId(feeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bang tinh tien nay da co hoa don");
        }
        LocalDate issue = issueDate == null ? LocalDate.now() : issueDate;
        LocalDate due = dueDate != null ? dueDate : defaultDueDate(issue, fee.getContract().getPaymentDueDay());
        if (due.isBefore(issue)) {
            throw bad("Han thanh toan phai sau ngay phat hanh");
        }
        Invoice inv = new Invoice();
        inv.setInvoiceCode(buildCode(fee));
        inv.setCalculatedFee(fee);
        inv.setTenant(fee.getContract().getTenant());
        inv.setIssueDate(issue);
        inv.setDueDate(due);
        inv.setTotalAmount(fee.getTotalCalculatedAmount());
        inv.setPaidAmount(BigDecimal.ZERO);
        inv.setPaymentStatus("UNPAID");
        inv.setShareLink(SHARE_PREFIX + UUID.randomUUID());
        inv = invoiceRepo.save(inv);
        invoiceRepo.flush();
        em.refresh(inv);

        addNotification(inv, "SYSTEM_APP", "SENT"); // UC 79: thong bao hoa don moi
        prepaymentService.autoDeduct(inv);          // UC 92: can tru tien du ky truoc
        return inv;
    }

    // ------------------------------------------------------------------ UC126: huy hoa don phat hanh nham

    /** UC126: chỉ hủy hóa đơn chưa phát sinh khoản thu và ghi nhật ký cùng transaction. */
    @Transactional
    public CancelInvoiceResponse cancel(Integer invoiceId, CancelInvoiceRequest req,
                                        Integer actorUserId, String ipAddress) {
        if (req == null || req.reason() == null || req.reason().isBlank()) {
            throw new BusinessException("Vui lòng nhập lý do hủy hóa đơn");
        }
        if (actorUserId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Không xác định được người thực hiện");
        }

        Invoice invoice = invoiceRepo.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> BusinessException.notFound("Hóa đơn"));
        if ("CANCELLED".equals(invoice.getPaymentStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hóa đơn đã được hủy trước đó");
        }

        BigDecimal paidAmount = invoice.getPaidAmount() == null ? BigDecimal.ZERO : invoice.getPaidAmount();
        boolean hasSuccessfulTransaction = paymentTransactionRepo
                .existsByInvoiceInvoiceIdAndStatus(invoiceId, "SUCCESS");
        boolean hasPartialCollection = partialPaymentLogRepo
                .existsByInvoiceInvoiceIdAndAmountGreaterThan(invoiceId, BigDecimal.ZERO);
        if (paidAmount.signum() > 0 || hasSuccessfulTransaction || hasPartialCollection) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Không thể hủy hóa đơn vì đã phát sinh khoản thu. Vui lòng xử lý giao dịch theo quy trình hoàn/đảo tiền.");
        }

        User actor = userRepo.findById(actorUserId)
                .orElseThrow(() -> BusinessException.notFound("Tài khoản người thực hiện"));
        LocalDateTime cancelledAt = LocalDateTime.now();
        invoice.setPaymentStatus("CANCELLED");

        UserActivityLog log = new UserActivityLog();
        log.setUser(actor);
        log.setActionType("CANCEL_INVOICE");
        log.setDescription("Hủy hóa đơn " + invoice.getInvoiceCode() + ". Lý do: " + req.reason().trim());
        log.setIpAddress(ipAddress);
        log.setCreatedAt(cancelledAt);
        activityLogRepo.save(log);
        invoiceRepo.flush();

        return new CancelInvoiceResponse(invoice.getInvoiceId(), invoice.getInvoiceCode(),
                invoice.getPaymentStatus(), req.reason().trim(), cancelledAt);
    }

    // ------------------------------------------------------------------ UC 78: chi tiet & in

    @Transactional(readOnly = true)
    public InvoiceDetail detail(Integer invoiceId) {
        Invoice inv = find(invoiceId);
        CalculatedMonthlyFee f = inv.getCalculatedFee();
        List<FeeLine> items = em.createQuery("select d from FeeItemDetail d "
                        + "where d.calculatedFee.calculatedFeeId = :id order by d.detailId", FeeItemDetail.class)
                .setParameter("id", f.getCalculatedFeeId()).getResultList().stream()
                .map(d -> new FeeLine(d.getItemName(), d.getQuantity(), d.getUnitPrice(), d.getSubtotal()))
                .toList();
        return new InvoiceDetail(toResponse(inv), f.getRentAmount(), f.getElectricAmount(), f.getWaterAmount(),
                f.getServiceAmount(), nz(f.getSurchargeAmount()), nz(f.getDiscountAmount()),
                nz(f.getLatePenaltyAmount()), items);
    }

    /** HTML san sang in (Ctrl+P / luu PDF). Xuat PDF file that dung util xuat PDF chung. */
    @Transactional(readOnly = true)
    public String printableHtml(Integer invoiceId) {
        InvoiceDetail d = detail(invoiceId);
        InvoiceResponse i = d.invoice();
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\"><title>")
                .append(esc(i.invoiceCode())).append("</title><style>")
                .append("body{font-family:Arial,sans-serif;max-width:720px;margin:24px auto}")
                .append("table{width:100%;border-collapse:collapse}td,th{border:1px solid #999;padding:6px}")
                .append("td.n,th.n{text-align:right}</style></head><body>")
                .append("<h2>HOA DON TIEN PHONG ").append(esc(i.monthYear())).append("</h2>")
                .append("<p>Ma hoa don: <b>").append(esc(i.invoiceCode())).append("</b><br>")
                .append("Phong: ").append(esc(i.roomCode())).append("<br>")
                .append("Khach thue: ").append(esc(i.tenantName())).append("<br>")
                .append("Ngay phat hanh: ").append(i.issueDate()).append(" - Han thanh toan: ")
                .append(i.dueDate()).append("</p>")
                .append("<table><tr><th>Khoan muc</th><th class=\"n\">SL</th><th class=\"n\">Don gia</th>")
                .append("<th class=\"n\">Thanh tien</th></tr>");
        for (FeeLine l : d.items()) {
            sb.append("<tr><td>").append(esc(l.itemName())).append("</td><td class=\"n\">")
                    .append(l.quantity().stripTrailingZeros().toPlainString()).append("</td><td class=\"n\">")
                    .append(l.unitPrice().toPlainString()).append("</td><td class=\"n\">")
                    .append(l.subtotal().toPlainString()).append("</td></tr>");
        }
        sb.append("</table><p>Tien phong: ").append(d.rentAmount().toPlainString())
                .append(" | Dien: ").append(d.electricAmount().toPlainString())
                .append(" | Nuoc: ").append(d.waterAmount().toPlainString())
                .append(" | Dich vu: ").append(d.serviceAmount().toPlainString())
                .append(" | Phu thu: ").append(d.surchargeAmount().toPlainString())
                .append(" | Mien giam: ").append(d.discountAmount().toPlainString())
                .append(" | Phat cham: ").append(d.latePenaltyAmount().toPlainString()).append("</p>")
                .append("<h3>Tong cong: ").append(i.totalAmount().toPlainString()).append("</h3>")
                .append("<p>Da thanh toan: ").append(i.paidAmount().toPlainString())
                .append(" - Con lai: ").append(i.remainingAmount().toPlainString())
                .append(" (").append(i.paymentStatus()).append(")</p></body></html>");
        return sb.toString();
    }

    // ------------------------------------------------------------------ UC 80, 81: gui email & website

    @Transactional
    public NotificationResponse sendEmail(Integer invoiceId) {
        Invoice inv = find(invoiceId);
        String email = inv.getTenant().getEmail();
        if (email == null || email.isBlank()) {
            throw bad("Khach thue chua co email");
        }
        return toResponse(addNotification(inv, "EMAIL", dispatchEmail(inv, "Hoa don " + inv.getInvoiceCode(), null)));
    }

    /** Gui hoa don len thong bao website (UC 81); lien ket chia se nam trong hoa don. */
    @Transactional
    public NotificationResponse sendToApp(Integer invoiceId) {
        Invoice inv = find(invoiceId);
        if (inv.getShareLink() == null) {
            inv.setShareLink(SHARE_PREFIX + UUID.randomUUID());
        }
        return toResponse(addNotification(inv, "SYSTEM_APP", "SENT"));
    }

    /** Xem hoa don qua lien ket chia se (khong can dang nhap). */
    @Transactional(readOnly = true)
    public InvoiceDetail detailByShareToken(String token) {
        Invoice inv = invoiceRepo.findByShareLink(SHARE_PREFIX + token)
                .orElseThrow(() -> notFound("Hoa don chia se"));
        return detail(inv.getInvoiceId());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> notifications(Integer invoiceId) {
        return notificationRepo.findByInvoiceInvoiceIdOrderBySentAtDesc(invoiceId).stream()
                .map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------ UC 82: nhac han / qua han (goi tu scheduler)

    /** Nhac cac hoa don den han sau dung {@code daysBefore} ngay. Tra ve so thong bao da tao. */
    @Transactional
    public int sendDueReminders(int daysBefore, LocalDate today) {
        LocalDate target = today.plusDays(daysBefore);
        LocalDateTime startOfDay = today.atStartOfDay();
        int count = 0;
        for (Invoice inv : invoiceRepo.findDueBetween(target, target)) {
            if (!notificationRepo.existsByInvoiceInvoiceIdAndStatusAndSentAtAfter(
                    inv.getInvoiceId(), ST_REMINDER, startOfDay.minusSeconds(1))) {
                addNotification(inv, "SYSTEM_APP", ST_REMINDER);
                count++;
            }
        }
        return count;
    }

    /** Chuyen hoa don qua han sang OVERDUE va thong bao (toi da 1 lan / 7 ngay / hoa don). */
    @Transactional
    public int markOverdueAndNotify(LocalDate today) {
        LocalDateTime weekAgo = today.minusDays(6).atStartOfDay();
        int count = 0;
        for (Invoice inv : invoiceRepo.findPastDue(today)) {
            calculator.refreshStatus(inv, today);
            if (!"OVERDUE".equals(inv.getPaymentStatus())) {
                continue;
            }
            if (!notificationRepo.existsByInvoiceInvoiceIdAndStatusAndSentAtAfter(
                    inv.getInvoiceId(), ST_OVERDUE, weekAgo)) {
                addNotification(inv, "SYSTEM_APP", ST_OVERDUE);
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ UC 83: dieu chinh + thong bao

    @Transactional
    public InvoiceResponse adjustTotal(Integer invoiceId, AdjustInvoiceRequest req) {
        Invoice inv = find(invoiceId);
        if (req.reason() == null || req.reason().isBlank()) {
            throw bad("Phai nhap ly do dieu chinh hoa don");
        }
        if (req.newTotalAmount() == null || req.newTotalAmount().signum() < 0) {
            throw bad("newTotalAmount khong hop le");
        }
        if ("CANCELLED".equals(inv.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hoa don da huy");
        }
        inv.setTotalAmount(req.newTotalAmount());
        calculator.refreshStatus(inv, LocalDate.now());
        addNotification(inv, "SYSTEM_APP", ST_ADJUSTED);
        String email = inv.getTenant().getEmail();
        if (email != null && !email.isBlank()) {
            addNotification(inv, "EMAIL", dispatchEmail(inv, "Hoa don " + inv.getInvoiceCode()
                    + " da duoc dieu chinh", "Ly do: " + req.reason().trim()));
        }
        return toResponse(inv);
    }

    /** Gui xac nhan da nhan tien (UC 90): thong bao website + email neu co. */
    @Transactional
    public void notifyPaymentConfirmed(Invoice inv, String note) {
        addNotification(inv, "SYSTEM_APP", "PAYMENT_CONFIRMED");
        String email = inv.getTenant().getEmail();
        if (email != null && !email.isBlank()) {
            addNotification(inv, "EMAIL", dispatchEmail(inv, "Xac nhan thanh toan hoa don " + inv.getInvoiceCode(), note));
        }
    }

    // ------------------------------------------------------------------ UC 84: tra cuu

    @Transactional(readOnly = true)
    public InvoiceResponse get(Integer invoiceId) {
        return toResponse(find(invoiceId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> search(String code, String roomCode, String monthYear, Integer propertyId,
                                        Integer tenantId, String status) {
        String c = blankToNull(code);
        String rc = blankToNull(roomCode);
        return new DynamicQuery("select i from Invoice i where 1 = 1")
                .and("lower(i.invoiceCode) like :code", "code", c == null ? null : "%" + c.toLowerCase() + "%")
                .and("lower(i.calculatedFee.room.roomCode) like :roomCode", "roomCode",
                        rc == null ? null : "%" + rc.toLowerCase() + "%")
                .and("i.calculatedFee.billingPeriod.monthYear = :monthYear", "monthYear", blankToNull(monthYear))
                .and("i.calculatedFee.room.property.propertyId = :propertyId", "propertyId", propertyId)
                .and("i.tenant.tenantId = :tenantId", "tenantId", tenantId)
                .and("i.paymentStatus = :status", "status", blankToNull(status))
                .orderBy("i.issueDate desc, i.invoiceId desc")
                .list(em, Invoice.class).stream().map(this::toResponse).toList();
    }

    /** Hoa don khach can thanh toan (chua tra het), han som nhat truoc. */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> payable(Integer tenantId) {
        return invoiceRepo.findByTenantTenantIdAndPaymentStatusInOrderByDueDateAsc(
                tenantId, List.of("UNPAID", "PARTIALLY_PAID", "OVERDUE")).stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------ UC 76: tong hop tien phai thu

    /** Tong hop theo thang cho toan khu. */
    @Transactional(readOnly = true)
    public List<MonthSummary> summaryByMonth(Integer propertyId) {
        List<MonthSummary> out = new ArrayList<>();
        for (Object[] row : invoiceRepo.summarizeByMonth(propertyId)) {
            BigDecimal total = nz((BigDecimal) row[2]);
            BigDecimal paid = nz((BigDecimal) row[3]);
            out.add(new MonthSummary((String) row[0], (Long) row[1], total, paid, total.subtract(paid)));
        }
        return out;
    }

    /** Bang tong hop chi tiet tung phong cua mot thang. */
    @Transactional(readOnly = true)
    public MonthDetailSummary summaryOfMonth(Integer propertyId, String monthYear) {
        List<RoomSummaryLine> rooms = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (Invoice i : invoiceRepo.findByPropertyAndMonth(propertyId, monthYear)) {
            BigDecimal p = calculator.paidOf(i);
            rooms.add(new RoomSummaryLine(i.getCalculatedFee().getRoom().getRoomId(),
                    i.getCalculatedFee().getRoom().getRoomCode(), i.getInvoiceCode(), i.getTotalAmount(), p,
                    i.getTotalAmount().subtract(p), i.getPaymentStatus()));
            total = total.add(i.getTotalAmount());
            paid = paid.add(p);
        }
        return new MonthDetailSummary(monthYear, total, paid, total.subtract(paid), rooms);
    }

    // ------------------------------------------------------------------ helpers

    /** Gui email (neu co gateway). Tra ve trang thai ghi vao bang thong bao. */
    private String dispatchEmail(Invoice inv, String subject, String extraNote) {
        InvoiceMailGateway gw = mailGateway.getIfAvailable();
        if (gw == null) {
            return "PENDING";
        }
        try {
            String link = inv.getShareLink() == null ? "" : "<p>Xem hoa don: " + esc(inv.getShareLink()) + "</p>";
            String body = "<p>Hoa don <b>" + esc(inv.getInvoiceCode()) + "</b> - tong "
                    + inv.getTotalAmount().toPlainString() + ", han thanh toan " + inv.getDueDate() + ".</p>"
                    + (extraNote == null ? "" : "<p>" + esc(extraNote) + "</p>") + link;
            gw.send(inv.getTenant().getEmail(), subject, body);
            return "SENT";
        } catch (RuntimeException ex) {
            return "FAILED";
        }
    }

    InvoiceNotification addNotification(Invoice inv, String channel, String status) {
        InvoiceNotification n = new InvoiceNotification();
        n.setInvoice(inv);
        n.setChannel(channel);
        n.setSentAt(LocalDateTime.now());
        n.setStatus(status);
        return notificationRepo.save(n);
    }

    private static LocalDate defaultDueDate(LocalDate issue, Integer dueDay) {
        int day = dueDay == null ? 5 : dueDay;
        YearMonth ym = YearMonth.from(issue);
        LocalDate d = ym.atDay(Math.min(day, ym.lengthOfMonth()));
        if (d.isBefore(issue)) {
            YearMonth next = ym.plusMonths(1);
            d = next.atDay(Math.min(day, next.lengthOfMonth()));
        }
        return d;
    }

    private String buildCode(CalculatedMonthlyFee fee) {
        String ym = fee.getBillingPeriod().getMonthYear().replace("-", "");
        return "INV-" + ym + "-" + fee.getRoom().getRoomCode() + "-" + fee.getCalculatedFeeId();
    }

    private Invoice find(Integer id) {
        return invoiceRepo.findById(id).orElseThrow(() -> notFound("Hoa don " + id));
    }

    InvoiceResponse toResponse(Invoice i) {
        CalculatedMonthlyFee f = i.getCalculatedFee();
        BigDecimal paid = calculator.paidOf(i);
        return new InvoiceResponse(i.getInvoiceId(), i.getInvoiceCode(), f.getCalculatedFeeId(),
                i.getTenant().getTenantId(), i.getTenant().getFullName(), f.getRoom().getRoomId(),
                f.getRoom().getRoomCode(), f.getBillingPeriod().getMonthYear(), i.getIssueDate(),
                i.getDueDate(), i.getTotalAmount(), paid, i.getTotalAmount().subtract(paid),
                i.getPaymentStatus(), i.getShareLink(), i.getPdfUrl());
    }

    private NotificationResponse toResponse(InvoiceNotification n) {
        return new NotificationResponse(n.getNotificationId(), n.getInvoice().getInvoiceId(), n.getChannel(),
                n.getSentAt(), n.getStatus());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Khong tim thay " + what);
    }
}
