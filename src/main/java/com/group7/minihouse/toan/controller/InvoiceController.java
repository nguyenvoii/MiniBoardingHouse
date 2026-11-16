package com.group7.minihouse.toan.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.group7.minihouse.toan.dto.InvoiceDtos.*;
import com.group7.minihouse.toan.service.InvoiceService;

/** API hoa don (UC 76, 78-84). */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    // UC 79
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse issue(@RequestBody IssueRequest req) {
        return service.issue(req);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<InvoiceResponse> issueBatch(@RequestBody IssueBatchRequest req) {
        return service.issueBatch(req);
    }

    // UC 84
    @GetMapping
    public List<InvoiceResponse> search(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "roomCode", required = false) String roomCode,
            @RequestParam(value = "monthYear", required = false) String monthYear,
            @RequestParam(value = "propertyId", required = false) Integer propertyId,
            @RequestParam(value = "tenantId", required = false) Integer tenantId,
            @RequestParam(value = "status", required = false) String status) {
        return service.search(code, roomCode, monthYear, propertyId, tenantId, status);
    }

    @GetMapping("/payable")
    public List<InvoiceResponse> payable(@RequestParam("tenantId") Integer tenantId) {
        return service.payable(tenantId);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable("id") Integer id) {
        return service.get(id);
    }

    // UC 78
    @GetMapping("/{id}/detail")
    public InvoiceDetail detail(@PathVariable("id") Integer id) {
        return service.detail(id);
    }

    @GetMapping(value = "/{id}/print", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String print(@PathVariable("id") Integer id) {
        return service.printableHtml(id);
    }

    // UC 80, 81
    @PostMapping("/{id}/send-email")
    public NotificationResponse sendEmail(@PathVariable("id") Integer id) {
        return service.sendEmail(id);
    }

    @PostMapping("/{id}/send-app")
    public NotificationResponse sendApp(@PathVariable("id") Integer id) {
        return service.sendToApp(id);
    }

    @GetMapping("/{id}/notifications")
    public List<NotificationResponse> notifications(@PathVariable("id") Integer id) {
        return service.notifications(id);
    }

    @GetMapping("/share/{token}")
    public InvoiceDetail shared(@PathVariable("token") String token) {
        return service.detailByShareToken(token);
    }

    // UC 83
    @PutMapping("/{id}/total")
    public InvoiceResponse adjust(@PathVariable("id") Integer id, @RequestBody AdjustInvoiceRequest req) {
        return service.adjustTotal(id, req);
    }

    // UC 76
    @GetMapping("/summary")
    public List<MonthSummary> summary(@RequestParam("propertyId") Integer propertyId) {
        return service.summaryByMonth(propertyId);
    }

    @GetMapping("/summary/{monthYear}")
    public MonthDetailSummary summaryOfMonth(@PathVariable("monthYear") String monthYear,
                                             @RequestParam("propertyId") Integer propertyId) {
        return service.summaryOfMonth(propertyId, monthYear);
    }
}
