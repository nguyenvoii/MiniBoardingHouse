package com.group7.minihouse.toan.controller;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.toan.dto.PaymentDtos.*;
import com.group7.minihouse.toan.service.PartialPaymentService;
import com.group7.minihouse.toan.service.PrepaymentService;
import com.group7.minihouse.toan.service.ReceiptService;

/** API thu tien: phieu thu, thanh toan mot phan, thanh toan du / huy (UC 89-92). */
@RestController
@RequestMapping("/api")
public class PaymentController {

    private final ReceiptService receiptService;
    private final PartialPaymentService partialService;
    private final PrepaymentService prepaymentService;

    public PaymentController(ReceiptService receiptService, PartialPaymentService partialService,
                             PrepaymentService prepaymentService) {
        this.receiptService = receiptService;
        this.partialService = partialService;
        this.prepaymentService = prepaymentService;
    }

    // UC 89
    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptResponse createReceipt(@RequestBody ReceiptRequest req) {
        return receiptService.create(req);
    }

    @GetMapping("/receipts")
    public List<ReceiptResponse> searchReceipts(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return receiptService.search(type, code, from, to);
    }

    @GetMapping("/receipts/{id}")
    public ReceiptResponse getReceipt(@PathVariable("id") Integer id) {
        return receiptService.get(id);
    }

    // UC 90
    @PostMapping("/receipts/{id}/send-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendConfirmation(@PathVariable("id") Integer id) {
        receiptService.sendConfirmation(id);
    }

    @GetMapping(value = "/receipts/{id}/print", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String printReceipt(@PathVariable("id") Integer id) {
        return receiptService.printableHtml(id);
    }

    // UC 91
    @PostMapping("/invoices/{invoiceId}/partial-payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PartialPaymentResponse recordPartial(@PathVariable("invoiceId") Integer invoiceId,
                                                @RequestBody PartialPaymentRequest req) {
        return partialService.record(invoiceId, req);
    }

    @GetMapping("/invoices/{invoiceId}/partial-payments")
    public PartialPaymentSummary partialSummary(@PathVariable("invoiceId") Integer invoiceId) {
        return partialService.summary(invoiceId);
    }

    @PutMapping("/partial-payments/{id}")
    public PartialPaymentResponse updatePartial(@PathVariable("id") Integer id,
                                                @RequestBody PartialPaymentRequest req) {
        return partialService.update(id, req);
    }

    // UC 92
    @GetMapping("/tenants/{tenantId}/prepayment")
    public PrepaymentResponse balance(@PathVariable("tenantId") Integer tenantId) {
        return prepaymentService.getBalance(tenantId);
    }

    @GetMapping("/tenants/{tenantId}/prepayment/deductions")
    public List<DeductionResponse> deductions(@PathVariable("tenantId") Integer tenantId) {
        return prepaymentService.listDeductions(tenantId);
    }

    @PostMapping("/invoices/{invoiceId}/overpayment/to-balance")
    public PrepaymentResponse overpaymentToBalance(@PathVariable("invoiceId") Integer invoiceId) {
        return prepaymentService.moveOverpaymentToBalance(invoiceId);
    }

    @PostMapping("/prepayments/deduct")
    @ResponseStatus(HttpStatus.CREATED)
    public DeductionResponse deduct(@RequestBody DeductRequest req) {
        return prepaymentService.deduct(req);
    }

    @DeleteMapping("/prepayments/deductions/{id}")
    public PrepaymentResponse undoDeduction(@PathVariable("id") Integer id) {
        return prepaymentService.undoDeduction(id);
    }

    @PostMapping("/prepayments/refund")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public PrepaymentResponse refund(@RequestBody RefundRequest req,
                                     @AuthenticationPrincipal AuthUser actor,
                                     HttpServletRequest http) {
        return prepaymentService.refund(req, CurrentUser.requireUserId(actor), http.getRemoteAddr());
    }

    @PostMapping("/payment-transactions/{id}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public void reverse(@PathVariable("id") Integer id, @AuthenticationPrincipal AuthUser actor,
                        HttpServletRequest http) {
        prepaymentService.reverseTransaction(id, CurrentUser.requireUserId(actor), http.getRemoteAddr());
    }
}
