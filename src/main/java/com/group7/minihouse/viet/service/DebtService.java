package com.group7.minihouse.viet.service;

import static com.group7.minihouse.viet.service.VietSupport.nz;

import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.DebtPenaltyLog;
import com.group7.minihouse.shared.entity.DebtProcessingLog;
import com.group7.minihouse.shared.entity.DebtReminder;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.PartialPaymentLog;
import com.group7.minihouse.shared.entity.PaymentTransaction;
import com.group7.minihouse.shared.entity.PrepaymentDeduction;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.TenantDebt;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.DebtDtos.DebtDetailResponse;
import com.group7.minihouse.viet.dto.DebtDtos.DebtResponse;
import com.group7.minihouse.viet.dto.DebtDtos.DebtSummaryResponse;
import com.group7.minihouse.viet.dto.DebtDtos.InvoiceHistoryItem;
import com.group7.minihouse.viet.dto.DebtDtos.PaymentHistoryItem;
import com.group7.minihouse.viet.dto.DebtDtos.PenaltyResponse;
import com.group7.minihouse.viet.dto.DebtDtos.ProcessingLogRequest;
import com.group7.minihouse.viet.dto.DebtDtos.ProcessingLogResponse;
import com.group7.minihouse.viet.dto.DebtDtos.RecalcResult;
import com.group7.minihouse.viet.dto.DebtDtos.ReminderResponse;
import com.group7.minihouse.viet.dto.DebtDtos.ReminderRunResult;
import com.group7.minihouse.viet.dto.DebtDtos.StatusSummary;
import com.group7.minihouse.viet.repository.DebtPenaltyLogRepository;
import com.group7.minihouse.viet.repository.DebtProcessingLogRepository;
import com.group7.minihouse.viet.repository.DebtReminderRepository;
import com.group7.minihouse.viet.repository.TenantDebtRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC93-100: cong no. Moi cap (khach thue, phong) co 1 dong trong tenant_debts:
 * total = tong con no cac hoa don chua tra xong; current = con no cua hoa don moi nhat;
 * opening = total - current (no ky truoc chuyen sang). Tien phat = lateFeePerDay cua hop dong x so ngay qua han.
 */
@Service
public class DebtService {

    static final Set<String> STATUSES = Set.of("NORMAL", "WARNING_OVERDUE", "LONG_TERM_DEBT", "CLEARED");
    /** Qua han tu 30 ngay tro len: no keo dai. */
    static final int LONG_TERM_DAYS = 30;
    /** Moc nhac no: tu ngay qua han thu 1, thu 7, thu 15. */
    static final int SECOND_REMINDER_DAYS = 7;
    static final int FINAL_WARNING_DAYS = 15;

    @PersistenceContext
    private EntityManager em;

    private final TenantDebtRepository debtRepository;
    private final DebtPenaltyLogRepository penaltyRepository;
    private final DebtReminderRepository reminderRepository;
    private final DebtProcessingLogRepository processingRepository;
    private final ActorResolver actorResolver;
    private final NotificationSender notificationSender;

    public DebtService(TenantDebtRepository debtRepository, DebtPenaltyLogRepository penaltyRepository,
                       DebtReminderRepository reminderRepository, DebtProcessingLogRepository processingRepository,
                       ActorResolver actorResolver, NotificationSender notificationSender) {
        this.debtRepository = debtRepository;
        this.penaltyRepository = penaltyRepository;
        this.reminderRepository = reminderRepository;
        this.processingRepository = processingRepository;
        this.actorResolver = actorResolver;
        this.notificationSender = notificationSender;
    }

    // ---------- Tra cuu (UC93, UC94, UC97, UC98) ----------

    @Transactional(readOnly = true)
    public PageResponse<DebtResponse> search(String status, Integer roomId, Integer tenantId, int page, int size) {
        String st = VietSupport.enumOrNull(status, STATUSES, "status");
        return new PageResponse<>(debtRepository
                .search(st, roomId, tenantId, VietSupport.page(page, size, Sort.by(Sort.Direction.DESC, "totalOutstandingDebt")))
                .map(DebtResponse::from));
    }

    @Transactional(readOnly = true)
    public DebtDetailResponse detail(Integer debtId) {
        TenantDebt d = find(debtId);
        List<PenaltyResponse> penalties = penaltyRepository.findByDebt_DebtIdOrderByCreatedAtDesc(debtId)
                .stream().map(PenaltyResponse::from).toList();
        List<ReminderResponse> reminders = reminderRepository.findByDebt_DebtIdOrderBySentAtDesc(debtId)
                .stream().map(ReminderResponse::from).toList();
        List<ProcessingLogResponse> logs = processingRepository.findByDebt_DebtIdOrderByRecordedAtDesc(debtId)
                .stream().map(ProcessingLogResponse::from).toList();
        BigDecimal latest = penalties.isEmpty() ? BigDecimal.ZERO : penalties.get(0).calculatedPenaltyAmount();
        return new DebtDetailResponse(DebtResponse.from(d), latest, penalties, reminders, logs);
    }

    /** UC94: cong no cua chinh nguoi thue (tenantId lay tu token o controller). */
    @Transactional(readOnly = true)
    public List<DebtResponse> myDebts(Integer tenantId) {
        return debtRepository.findByTenant_TenantIdOrderByUpdatedAtDesc(tenantId)
                .stream().map(DebtResponse::from).toList();
    }

    /** UC94: lich su hoa don / thanh toan cua chinh nguoi thue. */
    @Transactional(readOnly = true)
    public List<InvoiceHistoryItem> myInvoiceHistory(Integer tenantId) {
        List<Invoice> invoices = em.createQuery(
                "select i from Invoice i where i.tenant.tenantId = :t order by i.dueDate desc, i.invoiceId desc",
                Invoice.class).setParameter("t", tenantId).getResultList();
        return invoices.stream().map(i -> new InvoiceHistoryItem(i.getInvoiceId(), i.getInvoiceCode(),
                i.getIssueDate(), i.getDueDate(), i.getTotalAmount(), i.getPaidAmount(), i.getRemainingAmount(),
                i.getPaymentStatus())).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryItem> myPayments(Integer tenantId, Integer invoiceId) {
        var owned = em.createQuery("select i.invoiceId from Invoice i"
                        + " where i.invoiceId = :invoice and i.tenant.tenantId = :tenant", Integer.class)
                .setParameter("invoice", invoiceId).setParameter("tenant", tenantId).getResultList();
        if (owned.isEmpty()) {
            throw BusinessException.notFound("Hóa đơn");
        }

        var payments = new ArrayList<PaymentHistoryItem>();
        em.createQuery("select t from PaymentTransaction t where t.invoice.invoiceId = :invoice and t.invoice.tenant.tenantId = :tenant"
                        + " and t.status in ('SUCCESS','REVERSED')", PaymentTransaction.class)
                .setParameter("invoice", invoiceId).setParameter("tenant", tenantId).getResultList().forEach(t ->
                        payments.add(new PaymentHistoryItem(t.getTransactionId(), "TRANSACTION",
                                t.getPaymentMethod(), t.getStatus(), t.getAmountPaid(), t.getPaymentDate())));
        em.createQuery("select p from PartialPaymentLog p where p.invoice.invoiceId = :invoice"
                        + " and p.invoice.tenant.tenantId = :tenant", PartialPaymentLog.class)
                .setParameter("invoice", invoiceId).setParameter("tenant", tenantId).getResultList().forEach(p ->
                        payments.add(new PaymentHistoryItem(p.getPartialLogId(), "INSTALLMENT",
                                null, "SUCCESS", p.getAmount(), p.getPaidDate())));
        em.createQuery("select d from PrepaymentDeduction d where d.targetInvoice.invoiceId = :invoice"
                        + " and d.targetInvoice.tenant.tenantId = :tenant",
                        PrepaymentDeduction.class)
                .setParameter("invoice", invoiceId).setParameter("tenant", tenantId).getResultList().forEach(d ->
                        payments.add(new PaymentHistoryItem(d.getDeductionId(), "PREPAYMENT",
                                null, "SUCCESS", d.getDeductedAmount(), d.getDeductedAt())));
        return payments.stream().sorted(Comparator.comparing(PaymentHistoryItem::paidAt).reversed()
                .thenComparing(PaymentHistoryItem::source).thenComparing(PaymentHistoryItem::id)).toList();
    }

    @Transactional(readOnly = true)
    public DebtSummaryResponse summary() {
        List<StatusSummary> byStatus = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long debtors = 0;
        for (Object[] row : debtRepository.summarizeByStatus()) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal amount = row[2] instanceof BigDecimal b ? b : BigDecimal.valueOf(((Number) row[2]).doubleValue());
            byStatus.add(new StatusSummary(status, count, amount));
            if (!"CLEARED".equals(status)) {
                total = total.add(amount);
                debtors += count;
            }
        }
        return new DebtSummaryResponse(total, debtors, byStatus);
    }

    /** UC98: xuat danh sach cong no ra file Excel (.xlsx). */
    @Transactional(readOnly = true)
    public byte[] exportExcel(String status) {
        String st = VietSupport.enumOrNull(status, STATUSES, "status");
        List<TenantDebt> debts = debtRepository.findAllForExport(st);
        String[] headers = {"Phong", "Khach thue", "No ky truoc", "No ky nay", "Tong con no", "So ngay qua han",
                "Trang thai", "Cap nhat luc"};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Cong no");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            Row hr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                hr.createCell(i).setCellValue(headers[i]);
                hr.getCell(i).setCellStyle(head);
            }
            int r = 1;
            for (TenantDebt d : debts) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(d.getRoom().getRoomCode());
                row.createCell(1).setCellValue(d.getTenant().getFullName());
                row.createCell(2).setCellValue(nz(d.getOpeningDebtBalance()).doubleValue());
                row.createCell(3).setCellValue(nz(d.getCurrentPeriodDebt()).doubleValue());
                row.createCell(4).setCellValue(nz(d.getTotalOutstandingDebt()).doubleValue());
                row.createCell(5).setCellValue(d.getOverdueDays() == null ? 0 : d.getOverdueDays());
                row.createCell(6).setCellValue(d.getDebtStatus());
                row.createCell(7).setCellValue(String.valueOf(d.getUpdatedAt()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Không xuất được file Excel");
        }
    }

    // ---------- Tinh lai cong no, tien phat (UC95, UC96, UC97) ----------

    /** Chay hang ngay (VietJobs) hoac bam tay: POST /api/debts/recalculate. */
    @Transactional
    public RecalcResult recalculate() {
        LocalDate today = LocalDate.now();
        List<Invoice> open = em.createQuery("select i from Invoice i join fetch i.calculatedFee f join fetch f.room"
                + " join fetch f.contract join fetch i.tenant"
                + " where i.paymentStatus in ('UNPAID','PARTIALLY_PAID','OVERDUE') and i.remainingAmount > 0",
                Invoice.class).getResultList();

        Map<String, List<Invoice>> groups = new LinkedHashMap<>();
        for (Invoice i : open) {
            String key = i.getTenant().getTenantId() + ":" + i.getCalculatedFee().getRoom().getRoomId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        Set<Integer> seen = new HashSet<>();
        int penalties = 0;
        for (List<Invoice> list : groups.values()) {
            Invoice latest = list.get(0);
            Invoice oldestOverdue = null;
            BigDecimal total = BigDecimal.ZERO;
            int overdue = 0;
            for (Invoice i : list) {
                total = total.add(remainingOf(i));
                if (isLater(i, latest)) {
                    latest = i;
                }
                if (i.getDueDate() != null) {
                    int days = (int) ChronoUnit.DAYS.between(i.getDueDate(), today);
                    if (days > overdue) {
                        overdue = days;
                        oldestOverdue = i;
                    }
                }
            }
            Integer tenantId = latest.getTenant().getTenantId();
            Integer roomId = latest.getCalculatedFee().getRoom().getRoomId();
            TenantDebt debt = debtRepository.findFirstByTenant_TenantIdAndRoom_RoomIdOrderByDebtIdDesc(tenantId, roomId)
                    .orElseGet(TenantDebt::new);
            BigDecimal current = remainingOf(latest);
            debt.setTenant(latest.getTenant());
            debt.setRoom(latest.getCalculatedFee().getRoom());
            debt.setInvoice(latest);
            debt.setCurrentPeriodDebt(current);
            debt.setOpeningDebtBalance(total.subtract(current));
            debt.setTotalOutstandingDebt(total);
            debt.setOverdueDays(overdue);
            debt.setDebtStatus(statusFor(total, overdue));
            debt.setUpdatedAt(LocalDateTime.now());
            debt = debtRepository.save(debt);
            seen.add(debt.getDebtId());

            if (overdue > 0 && oldestOverdue != null && logPenalty(debt, oldestOverdue, overdue)) {
                penalties++;
            }
        }

        int cleared = 0;
        for (TenantDebt d : debtRepository.findByDebtStatusNot("CLEARED")) {
            if (!seen.contains(d.getDebtId())) {
                d.setOpeningDebtBalance(BigDecimal.ZERO);
                d.setCurrentPeriodDebt(BigDecimal.ZERO);
                d.setTotalOutstandingDebt(BigDecimal.ZERO);
                d.setOverdueDays(0);
                d.setDebtStatus("CLEARED");
                d.setUpdatedAt(LocalDateTime.now());
                debtRepository.save(d);
                cleared++;
            }
        }
        return new RecalcResult(groups.size(), cleared, penalties);
    }

    private static boolean isLater(Invoice a, Invoice b) {
        if (a.getDueDate() == null || b.getDueDate() == null) {
            return a.getInvoiceId() > b.getInvoiceId();
        }
        int c = a.getDueDate().compareTo(b.getDueDate());
        return c > 0 || (c == 0 && a.getInvoiceId() > b.getInvoiceId());
    }

    /** Con no = tong - da tra; khong dung cot tinh remaining_amount vi entity trong cung giao dich co the con so cu. */
    private static BigDecimal remainingOf(Invoice i) {
        return nz(i.getTotalAmount()).subtract(nz(i.getPaidAmount()));
    }

    static String statusFor(BigDecimal total, int overdueDays) {
        if (total.signum() <= 0) {
            return "CLEARED";
        }
        if (overdueDays <= 0) {
            return "NORMAL";
        }
        return overdueDays >= LONG_TERM_DAYS ? "LONG_TERM_DEBT" : "WARNING_OVERDUE";
    }

    /** Ghi 1 dong phat moi khi so ngay qua han thay doi (tranh ghi trung trong cung 1 ngay). */
    private boolean logPenalty(TenantDebt debt, Invoice oldestOverdue, int overdueDays) {
        RentalContract contract = oldestOverdue.getCalculatedFee().getContract();
        BigDecimal rate = contract == null ? null : contract.getLateFeePerDay();
        if (rate == null || rate.signum() <= 0) {
            return false;
        }
        var last = penaltyRepository.findFirstByDebt_DebtIdOrderByCreatedAtDesc(debt.getDebtId());
        if (last.isPresent() && last.get().getOverdueDays() == overdueDays) {
            return false;
        }
        DebtPenaltyLog log = new DebtPenaltyLog();
        log.setDebt(debt);
        log.setOverdueDays(overdueDays);
        log.setDailyPenaltyRate(rate);
        log.setCalculatedPenaltyAmount(rate.multiply(BigDecimal.valueOf(overdueDays)));
        log.setCreatedAt(LocalDateTime.now());
        penaltyRepository.save(log);
        return true;
    }

    // ---------- Nhac no tu dong (UC99) ----------

    @Transactional
    public ReminderRunResult sendReminders() {
        int sent = 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        for (TenantDebt d : debtRepository.findByDebtStatusNotOrderByDebtId("CLEARED")) {
            em.refresh(d, LockModeType.PESSIMISTIC_WRITE);
            if ("CLEARED".equals(d.getDebtStatus())) {
                continue;
            }
            int overdue = d.getOverdueDays() == null ? 0 : d.getOverdueDays();
            if (overdue <= 0) {
                continue;
            }
            // Khach da hen ngay tra chua toi han thi chua nhac
            var lastLog = processingRepository.findFirstByDebt_DebtIdOrderByRecordedAtDesc(d.getDebtId());
            if (lastLog.isPresent() && lastLog.get().getPromisedPaymentDate() != null
                    && !lastLog.get().getPromisedPaymentDate().isBefore(today)) {
                continue;
            }
            String level = overdue >= FINAL_WARNING_DAYS ? "FINAL_WARNING"
                    : overdue >= SECOND_REMINDER_DAYS ? "SECOND_REMINDER" : "FIRST_REMINDER";
            LocalDateTime since = today.minusDays(overdue).atStartOfDay();
            if (reminderRepository.existsByDebt_DebtIdAndReminderLevelAndSentAtAfter(d.getDebtId(), level, since)) {
                continue;
            }
            DebtReminder r = new DebtReminder();
            r.setDebt(d);
            r.setReminderLevel(level);
            String message = buildMessage(d, level, overdue);
            r.setMessageContent(message);
            r.setSentAt(now);
            // Dua nhac no vao trung tam thong bao cua khach (UC123); neu co mau dang bat (UC121) thi dung mau
            String money = NumberFormat.getInstance(new Locale("vi", "VN")).format(d.getTotalOutstandingDebt()) + " đ";
            boolean delivered = notificationSender.notifyTenant(d.getTenant().getTenantId(), templateCodeFor(level),
                    titleFor(level), message, Map.of("room", String.valueOf(d.getRoom().getRoomCode()),
                            "amount", money, "overdueDays", String.valueOf(overdue)), "DEBT", d.getDebtId());
            if (delivered) {
                reminderRepository.save(r);
                sent++;
            }
        }
        return new ReminderRunResult(sent);
    }

    static String templateCodeFor(String level) {
        return switch (level) {
            case "FIRST_REMINDER" -> "DEBT_REMINDER_1";
            case "SECOND_REMINDER" -> "DEBT_REMINDER_2";
            default -> "DEBT_FINAL_WARNING";
        };
    }

    private static String titleFor(String level) {
        return switch (level) {
            case "FIRST_REMINDER" -> "Nhắc nợ lần 1";
            case "SECOND_REMINDER" -> "Nhắc nợ lần 2";
            default -> "Cảnh báo nợ quá hạn";
        };
    }

    private static String buildMessage(TenantDebt d, String level, int overdue) {
        String money = NumberFormat.getInstance(new Locale("vi", "VN")).format(d.getTotalOutstandingDebt()) + " đ";
        String room = d.getRoom().getRoomCode();
        return switch (level) {
            case "FIRST_REMINDER" -> "Phòng " + room + " đang nợ " + money + ", quá hạn " + overdue
                    + " ngày. Vui lòng thanh toán sớm.";
            case "SECOND_REMINDER" -> "Nhắc lần 2: phòng " + room + " còn nợ " + money + ", quá hạn " + overdue
                    + " ngày. Vui lòng thanh toán hoặc liên hệ chủ trọ.";
            default -> "CẢNH BÁO: phòng " + room + " nợ " + money + ", quá hạn " + overdue
                    + " ngày. Nếu không thanh toán, chủ trọ sẽ áp dụng các biện pháp theo hợp đồng.";
        };
    }

    // ---------- Ghi nhan xu ly cong no (UC100) ----------

    @Transactional
    public ProcessingLogResponse addProcessingLog(Integer debtId, ProcessingLogRequest req, Integer actorId) {
        TenantDebt d = find(debtId);
        if (req == null || (req.promisedPaymentDate() == null && VietSupport.blankToNull(req.delayReason()) == null
                && VietSupport.blankToNull(req.staffNote()) == null)) {
            throw new BusinessException("Cần ít nhất 1 thông tin: ngày hẹn trả, lý do chậm hoặc ghi chú");
        }
        if (req.promisedPaymentDate() != null && req.promisedPaymentDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Ngày hẹn trả không được ở quá khứ");
        }
        DebtProcessingLog log = new DebtProcessingLog();
        log.setDebt(d);
        log.setPromisedPaymentDate(req.promisedPaymentDate());
        log.setDelayReason(VietSupport.blankToNull(req.delayReason()));
        log.setStaffNote(VietSupport.blankToNull(req.staffNote()));
        log.setRecordedBy(actorResolver.require(actorId));
        log.setRecordedAt(LocalDateTime.now());
        return ProcessingLogResponse.from(processingRepository.save(log));
    }

    @Transactional(readOnly = true)
    public List<ProcessingLogResponse> processingLogs(Integer debtId) {
        find(debtId);
        return processingRepository.findByDebt_DebtIdOrderByRecordedAtDesc(debtId)
                .stream().map(ProcessingLogResponse::from).toList();
    }

    private TenantDebt find(Integer debtId) {
        return debtRepository.findById(debtId).orElseThrow(() -> BusinessException.notFound("Công nợ"));
    }
}
