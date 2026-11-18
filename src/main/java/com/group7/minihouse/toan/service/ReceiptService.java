package com.group7.minihouse.toan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.PaymentDtos.ReceiptRequest;
import com.group7.minihouse.toan.dto.PaymentDtos.ReceiptResponse;
import com.group7.minihouse.shared.entity.PaymentTransaction;
import com.group7.minihouse.shared.entity.ReceiptVoucher;
import com.group7.minihouse.toan.repository.ReceiptVoucherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Phieu thu (UC 89) va xac nhan / bien nhan thanh toan (UC 90). */
@Service
public class ReceiptService {

    private static final Set<String> TYPES = Set.of("MONTHLY_RENT", "DEPOSIT", "EXTRA_SERVICE");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReceiptVoucherRepository receiptRepo;
    private final InvoiceService invoiceService;
    private final ToanActivityWriter activity;

    @PersistenceContext
    private EntityManager em;

    public ReceiptService(ReceiptVoucherRepository receiptRepo, InvoiceService invoiceService,
                          ToanActivityWriter activity) {
        this.activity = activity;
        this.receiptRepo = receiptRepo;
        this.invoiceService = invoiceService;
    }

    /** Dung noi bo (vd DepositService lap phieu thu coc co ghi chu dung mau cua hop dong). */
    @Transactional
    public ReceiptResponse create(ReceiptRequest req) {
        return doCreate(req, null, null);
    }

    /**
     * Duong API (POST /api/receipts): M12 khong cho lap phieu loai DEPOSIT o day vi tien coc da thu cua hop dong duoc suy
     * ra tu phieu DEPOSIT co ghi chu dung mau; ai lap tay duoc phieu loai nay se lam tang tien hoan coc. Thu coc di qua
     * POST /api/contracts/{id}/deposit.
     */
    @Transactional
    public ReceiptResponse create(ReceiptRequest req, Integer actorId, String ip) {
        if (req != null && "DEPOSIT".equalsIgnoreCase(req.voucherType())) {
            throw bad("Phieu thu coc chi lap qua muc Thu coc cua hop dong");
        }
        return doCreate(req, actorId, ip);
    }

    /**
     * Phieu thu tien phong (MONTHLY_RENT) bat buoc gan giao dich thu tien da thanh cong; da gan giao dich thi so tien
     * phieu khong duoc vuot so tien giao dich (tinh ca cac phieu da lap truoc do).
     */
    private ReceiptResponse doCreate(ReceiptRequest req, Integer actorId, String ip) {
        if (req.voucherType() == null || !TYPES.contains(req.voucherType().toUpperCase())) {
            throw bad("voucherType phai thuoc " + TYPES);
        }
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw bad("So tien phai lon hon 0");
        }
        PaymentTransaction tx = null;
        if (req.transactionId() != null) {
            tx = em.find(PaymentTransaction.class, req.transactionId());
            if (tx == null) {
                throw notFound("Giao dich " + req.transactionId());
            }
            if (!"SUCCESS".equals(tx.getStatus())) {
                throw new BusinessException(HttpStatus.CONFLICT, "Chi lap phieu thu cho giao dich da thu thanh cong");
            }
            BigDecimal issued = receiptRepo.sumByTransaction(tx.getTransactionId());
            if (issued.add(req.amount()).compareTo(tx.getAmountPaid()) > 0) {
                throw bad("So tien phieu thu vuot so tien giao dich (giao dich " + tx.getAmountPaid()
                        + ", da lap phieu " + issued + ")");
            }
        } else if ("MONTHLY_RENT".equals(req.voucherType().toUpperCase())) {
            throw bad("Phieu thu tien phong phai gan voi mot giao dich thu tien (transactionId)");
        }
        ReceiptVoucher v = new ReceiptVoucher();
        v.setVoucherCode(nextCode());
        v.setTransaction(tx);
        v.setVoucherType(req.voucherType().toUpperCase());
        v.setAmount(req.amount());
        v.setNote(req.note());
        v.setCreatedAt(LocalDateTime.now());
        ReceiptVoucher saved = receiptRepo.save(v);
        activity.log(actorId, "CREATE_RECEIPT", "Lập phiếu thu " + saved.getVoucherCode() + " số tiền "
                + saved.getAmount(), ip);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(Integer voucherId) {
        return toResponse(find(voucherId));
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> search(String type, String code, LocalDate from, LocalDate to) {
        String t = blankToNull(type);
        String c = blankToNull(code);
        return new DynamicQuery("select v from ReceiptVoucher v where 1 = 1")
                .and("v.voucherType = :type", "type", t == null ? null : t.toUpperCase())
                .and("lower(v.voucherCode) like :code", "code", c == null ? null : "%" + c.toLowerCase() + "%")
                .and("v.createdAt >= :fromTime", "fromTime", from == null ? null : from.atStartOfDay())
                .and("v.createdAt < :toTime", "toTime", to == null ? null : to.plusDays(1).atStartOfDay())
                .orderBy("v.createdAt desc")
                .list(em, ReceiptVoucher.class).stream().map(this::toResponse).toList();
    }

    /** UC 90: gui xac nhan thanh toan cho khach (can phieu thu gan voi giao dich cua mot hoa don). */
    @Transactional
    public void sendConfirmation(Integer voucherId) {
        ReceiptVoucher v = find(voucherId);
        if (v.getTransaction() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "Phieu thu chua gan voi giao dich/hoa don");
        }
        invoiceService.notifyPaymentConfirmed(v.getTransaction().getInvoice(),
                "Da nhan " + v.getAmount().toPlainString() + " - phieu thu " + v.getVoucherCode());
    }

    /** Bien nhan HTML de in / luu PDF. */
    @Transactional(readOnly = true)
    public String printableHtml(Integer voucherId) {
        ReceiptVoucher v = find(voucherId);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\"><title>")
                .append(esc(v.getVoucherCode())).append("</title></head>")
                .append("<body style=\"font-family:Arial,sans-serif;max-width:560px;margin:24px auto\">")
                .append("<h2>PHIEU THU</h2><p>So: <b>").append(esc(v.getVoucherCode())).append("</b><br>")
                .append("Loai: ").append(esc(v.getVoucherType())).append("<br>")
                .append("Ngay: ").append(v.getCreatedAt().toLocalDate()).append("</p>");
        if (v.getTransaction() != null) {
            sb.append("<p>Hoa don: ").append(esc(v.getTransaction().getInvoice().getInvoiceCode()))
                    .append("<br>Phuong thuc: ").append(esc(v.getTransaction().getPaymentMethod())).append("</p>");
        }
        sb.append("<h3>So tien: ").append(v.getAmount().toPlainString()).append(" VND</h3>");
        if (v.getNote() != null) {
            sb.append("<p>Ghi chu: ").append(esc(v.getNote())).append("</p>");
        }
        return sb.append("</body></html>").toString();
    }

    private String nextCode() {
        String prefix = "PT-" + LocalDate.now().format(DAY) + "-";
        long n = receiptRepo.countByVoucherCodeStartingWith(prefix) + 1;
        String code = prefix + String.format("%04d", n);
        while (receiptRepo.existsByVoucherCode(code)) { // dem +1 co the trung khi da co phieu bi xoa / lech so
            code = prefix + String.format("%04d", ++n);
        }
        return code;
    }

    private ReceiptVoucher find(Integer id) {
        return receiptRepo.findById(id).orElseThrow(() -> notFound("Phieu thu " + id));
    }

    private ReceiptResponse toResponse(ReceiptVoucher v) {
        return new ReceiptResponse(v.getVoucherId(), v.getVoucherCode(),
                v.getTransaction() == null ? null : v.getTransaction().getTransactionId(),
                v.getVoucherType(), v.getAmount(), v.getNote(), v.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static BusinessException bad(String msg) {
        return new BusinessException(HttpStatus.BAD_REQUEST, msg);
    }

    private static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, "Khong tim thay " + what);
    }
}
