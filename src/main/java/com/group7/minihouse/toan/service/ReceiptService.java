package com.group7.minihouse.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.group7.minihouse.dto.PaymentDtos.ReceiptRequest;
import com.group7.minihouse.dto.PaymentDtos.ReceiptResponse;
import com.group7.minihouse.entity.PaymentTransaction;
import com.group7.minihouse.entity.ReceiptVoucher;
import com.group7.minihouse.repository.ReceiptVoucherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Phieu thu (UC 89) va xac nhan / bien nhan thanh toan (UC 90). */
@Service
public class ReceiptService {

    private static final Set<String> TYPES = Set.of("MONTHLY_RENT", "DEPOSIT", "EXTRA_SERVICE");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReceiptVoucherRepository receiptRepo;
    private final InvoiceService invoiceService;

    @PersistenceContext
    private EntityManager em;

    public ReceiptService(ReceiptVoucherRepository receiptRepo, InvoiceService invoiceService) {
        this.receiptRepo = receiptRepo;
        this.invoiceService = invoiceService;
    }

    @Transactional
    public ReceiptResponse create(ReceiptRequest req) {
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
        }
        ReceiptVoucher v = new ReceiptVoucher();
        v.setVoucherCode(nextCode());
        v.setTransaction(tx);
        v.setVoucherType(req.voucherType().toUpperCase());
        v.setAmount(req.amount());
        v.setNote(req.note());
        v.setCreatedAt(LocalDateTime.now());
        return toResponse(receiptRepo.save(v));
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phieu thu chua gan voi giao dich/hoa don");
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
        return prefix + String.format("%04d", receiptRepo.countByVoucherCodeStartingWith(prefix) + 1);
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

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Khong tim thay " + what);
    }
}
