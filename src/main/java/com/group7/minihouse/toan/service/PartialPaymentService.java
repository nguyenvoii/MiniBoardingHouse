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
import com.group7.minihouse.shared.entity.PartialPaymentLog;
import com.group7.minihouse.toan.repository.InvoiceRepository;
import com.group7.minihouse.toan.repository.PartialPaymentLogRepository;

/**
 * Thanh toan mot phan / nhieu lan cho mot hoa don (UC 91). Moi duong ghi deu khoa dong hoa don (tranh hai nguoi
 * cung thu de len nhau) va ghi nhat ky nguoi thao tac. Cac dot nay duoc tinh vao bao cao doanh thu (ReportService).
 */
@Service
public class PartialPaymentService {

    private final PartialPaymentLogRepository logRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceBalanceCalculator calculator;
    private final ToanActivityWriter activity;

    public PartialPaymentService(PartialPaymentLogRepository logRepo, InvoiceRepository invoiceRepo,
                                 InvoiceBalanceCalculator calculator, ToanActivityWriter activity) {
        this.logRepo = logRepo;
        this.invoiceRepo = invoiceRepo;
        this.calculator = calculator;
        this.activity = activity;
    }

    @Transactional
    public PartialPaymentResponse record(Integer invoiceId, PartialPaymentRequest req) {
        return record(invoiceId, req, null, null);
    }

    @Transactional
    public PartialPaymentResponse record(Integer invoiceId, PartialPaymentRequest req, Integer actorId, String ip) {
        Invoice inv = lockInvoice(invoiceId);
        if ("CANCELLED".equals(inv.getPaymentStatus()) || "PAID".equals(inv.getPaymentStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Hoa don dang o trang thai " + inv.getPaymentStatus());
        }
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw bad("So tien phai lon hon 0");
        }
        if (req.amount().compareTo(calculator.remainingOf(inv)) > 0) {
            throw bad("So tien lon hon so con phai tra (" + calculator.remainingOf(inv)
                    + "). Phan thua xu ly o chuc nang thanh toan du");
        }
        PartialPaymentLog log = new PartialPaymentLog();
        log.setInvoice(inv);
        log.setInstallmentNumber(logRepo.maxInstallment(invoiceId) + 1);
        log.setAmount(req.amount());
        log.setPaidDate(req.paidDate() == null ? LocalDateTime.now() : req.paidDate());
        log = logRepo.save(log);
        calculator.setPaid(inv, calculator.paidOf(inv).add(req.amount()), LocalDate.now());
        activity.log(actorId, "PARTIAL_PAYMENT", "Ghi nhận thanh toán một phần " + req.amount() + " (đợt "
                + log.getInstallmentNumber() + ") cho hóa đơn " + inv.getInvoiceCode(), ip);
        return toResponse(log);
    }

    /** Sua so tien cua mot dot da ghi nhan (ghi nham). Khong sua duoc dot cua hoa don da huy. */
    @Transactional
    public PartialPaymentResponse update(Integer logId, PartialPaymentRequest req) {
        return update(logId, req, null, null);
    }

    @Transactional
    public PartialPaymentResponse update(Integer logId, PartialPaymentRequest req, Integer actorId, String ip) {
        PartialPaymentLog log = logRepo.findById(logId).orElseThrow(() -> notFound("Dot thanh toan " + logId));
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw bad("So tien phai lon hon 0");
        }
        Invoice inv = lockInvoice(log.getInvoice().getInvoiceId());
        if ("CANCELLED".equals(inv.getPaymentStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hoa don da huy, khong sua duoc dot thanh toan");
        }
        BigDecimal oldAmount = log.getAmount();
        BigDecimal newPaid = calculator.paidOf(inv).subtract(oldAmount).add(req.amount());
        if (newPaid.compareTo(inv.getTotalAmount()) > 0) {
            throw bad("Tong da tra vuot qua tong hoa don");
        }
        log.setAmount(req.amount());
        if (req.paidDate() != null) {
            log.setPaidDate(req.paidDate());
        }
        calculator.setPaid(inv, newPaid, LocalDate.now());
        activity.log(actorId, "PARTIAL_PAYMENT_EDIT", "Sửa đợt thanh toán " + log.getInstallmentNumber() + " của hóa đơn "
                + inv.getInvoiceCode() + " từ " + oldAmount + " thành " + req.amount(), ip);
        return toResponse(log);
    }

    @Transactional(readOnly = true)
    public PartialPaymentSummary summary(Integer invoiceId) {
        Invoice inv = findInvoice(invoiceId);
        List<PartialPaymentResponse> list = logRepo.findByInvoiceInvoiceIdOrderByInstallmentNumberAsc(invoiceId)
                .stream().map(this::toResponse).toList();
        BigDecimal paid = calculator.paidOf(inv);
        return new PartialPaymentSummary(invoiceId, inv.getTotalAmount(), paid,
                calculator.remainingOf(inv), inv.getPaymentStatus(), list);
    }

    private Invoice lockInvoice(Integer id) {
        return invoiceRepo.findByIdForUpdate(id).orElseThrow(() -> notFound("Hoa don " + id));
    }

    private Invoice findInvoice(Integer id) {
        return invoiceRepo.findById(id).orElseThrow(() -> notFound("Hoa don " + id));
    }

    private PartialPaymentResponse toResponse(PartialPaymentLog l) {
        return new PartialPaymentResponse(l.getPartialLogId(), l.getInvoice().getInvoiceId(),
                l.getInstallmentNumber(), l.getAmount(), l.getPaidDate());
    }

    private static BusinessException bad(String msg) {
        return new BusinessException(HttpStatus.BAD_REQUEST, msg);
    }

    private static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, "Khong tim thay " + what);
    }
}
