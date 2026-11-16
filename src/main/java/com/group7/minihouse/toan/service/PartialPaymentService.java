package com.group7.minihouse.toan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.group7.minihouse.toan.dto.PaymentDtos.*;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.PartialPaymentLog;
import com.group7.minihouse.toan.repository.InvoiceRepository;
import com.group7.minihouse.toan.repository.PartialPaymentLogRepository;

/** Thanh toan mot phan / nhieu lan cho mot hoa don (UC 91). */
@Service
public class PartialPaymentService {

    private final PartialPaymentLogRepository logRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceBalanceCalculator calculator;

    public PartialPaymentService(PartialPaymentLogRepository logRepo, InvoiceRepository invoiceRepo,
                                 InvoiceBalanceCalculator calculator) {
        this.logRepo = logRepo;
        this.invoiceRepo = invoiceRepo;
        this.calculator = calculator;
    }

    @Transactional
    public PartialPaymentResponse record(Integer invoiceId, PartialPaymentRequest req) {
        Invoice inv = findInvoice(invoiceId);
        if ("CANCELLED".equals(inv.getPaymentStatus()) || "PAID".equals(inv.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
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
        log.setInstallmentNumber((int) logRepo.countByInvoiceInvoiceId(invoiceId) + 1);
        log.setAmount(req.amount());
        log.setPaidDate(req.paidDate() == null ? LocalDateTime.now() : req.paidDate());
        log = logRepo.save(log);
        calculator.setPaid(inv, calculator.paidOf(inv).add(req.amount()), LocalDate.now());
        return toResponse(log);
    }

    /** Sua so tien cua mot dot da ghi nhan (ghi nham). */
    @Transactional
    public PartialPaymentResponse update(Integer logId, PartialPaymentRequest req) {
        PartialPaymentLog log = logRepo.findById(logId).orElseThrow(() -> notFound("Dot thanh toan " + logId));
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw bad("So tien phai lon hon 0");
        }
        Invoice inv = log.getInvoice();
        BigDecimal newPaid = calculator.paidOf(inv).subtract(log.getAmount()).add(req.amount());
        if (newPaid.compareTo(inv.getTotalAmount()) > 0) {
            throw bad("Tong da tra vuot qua tong hoa don");
        }
        log.setAmount(req.amount());
        if (req.paidDate() != null) {
            log.setPaidDate(req.paidDate());
        }
        calculator.setPaid(inv, newPaid, LocalDate.now());
        return toResponse(log);
    }

    @Transactional(readOnly = true)
    public PartialPaymentSummary summary(Integer invoiceId) {
        Invoice inv = findInvoice(invoiceId);
        List<PartialPaymentResponse> list = logRepo.findByInvoiceInvoiceIdOrderByInstallmentNumberAsc(invoiceId)
                .stream().map(this::toResponse).toList();
        BigDecimal paid = calculator.paidOf(inv);
        return new PartialPaymentSummary(invoiceId, inv.getTotalAmount(), paid,
                inv.getTotalAmount().subtract(paid), inv.getPaymentStatus(), list);
    }

    private Invoice findInvoice(Integer id) {
        return invoiceRepo.findById(id).orElseThrow(() -> notFound("Hoa don " + id));
    }

    private PartialPaymentResponse toResponse(PartialPaymentLog l) {
        return new PartialPaymentResponse(l.getPartialLogId(), l.getInvoice().getInvoiceId(),
                l.getInstallmentNumber(), l.getAmount(), l.getPaidDate());
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Khong tim thay " + what);
    }
}
