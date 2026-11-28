package com.group7.minihouse.toan.controller;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.toan.dto.CancelInvoiceRequest;
import com.group7.minihouse.toan.dto.CancelInvoiceResponse;
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

    /** UC126: hủy hóa đơn phát hành nhầm khi chưa phát sinh khoản thu. */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    public ApiResponse<CancelInvoiceResponse> cancel(
            @PathVariable("id") Integer id,
            @Valid @RequestBody CancelInvoiceRequest req,
            @AuthenticationPrincipal AuthUser actor,
            HttpServletRequest http) {
        return ApiResponse.ok("Đã hủy hóa đơn", service.cancel(
                id, req, CurrentUser.requireUserId(actor), http.getRemoteAddr()));
    }

    // UC 79
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<InvoiceResponse> issue(@Valid @RequestBody IssueRequest req,
            @AuthenticationPrincipal AuthUser actor, HttpServletRequest http) {
        return ApiResponse.ok("Đã phát hành hóa đơn", service.issue(req, CurrentUser.requireUserId(actor), http.getRemoteAddr()));
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<List<InvoiceResponse>> issueBatch(@Valid @RequestBody IssueBatchRequest req,
            @AuthenticationPrincipal AuthUser actor, HttpServletRequest http) {
        return ApiResponse.ok("Đã phát hành hóa đơn theo lô",
                service.issueBatch(req, CurrentUser.requireUserId(actor), http.getRemoteAddr()));
    }

    /** Nhan vien thu tien/quan ly doc het; nguoi thue (TENANT) chi doc hoa don cua chinh minh (khi bat bao mat). */
    private static final String READ_ROLES = "hasAnyRole('LANDLORD','MANAGER','CASHIER','TENANT')";

    /** Nguoi thue chi xem duoc hoa don cua minh: khac chu thi tra 404 (khong lo hoa don do co ton tai hay khong). */
    private void requireOwnInvoice(AuthUser actor, Integer invoiceId) {
        if (actor != null && actor.isTenant()) {
            Integer own = CurrentUser.requireTenantId(actor);
            if (!own.equals(service.get(invoiceId).tenantId())) {
                throw com.group7.minihouse.shared.exception.BusinessException.notFound("Hóa đơn " + invoiceId);
            }
        }
    }

    // UC 84
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<InvoiceResponse> search(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "roomCode", required = false) String roomCode,
            @RequestParam(value = "monthYear", required = false) String monthYear,
            @RequestParam(value = "propertyId", required = false) Integer propertyId,
            @RequestParam(value = "tenantId", required = false) Integer tenantId,
            @RequestParam(value = "status", required = false) String status,
            @AuthenticationPrincipal AuthUser actor) {
        Integer tid = actor != null && actor.isTenant() ? CurrentUser.requireTenantId(actor) : tenantId;
        return service.search(code, roomCode, monthYear, propertyId, tid, status);
    }

    @GetMapping("/payable")
    @PreAuthorize(READ_ROLES)
    public List<InvoiceResponse> payable(@RequestParam(value = "tenantId", required = false) Integer tenantId,
                                         @AuthenticationPrincipal AuthUser actor) {
        Integer tid = actor != null && actor.isTenant() ? CurrentUser.requireTenantId(actor) : tenantId;
        if (tid == null) {
            throw new com.group7.minihouse.shared.exception.BusinessException("Cần tenantId");
        }
        return service.payable(tid);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public InvoiceResponse get(@PathVariable("id") Integer id, @AuthenticationPrincipal AuthUser actor) {
        requireOwnInvoice(actor, id);
        return service.get(id);
    }

    // UC 78
    @GetMapping("/{id}/detail")
    @PreAuthorize(READ_ROLES)
    public InvoiceDetail detail(@PathVariable("id") Integer id, @AuthenticationPrincipal AuthUser actor) {
        requireOwnInvoice(actor, id);
        return service.detail(id);
    }

    @GetMapping(value = "/{id}/print", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @PreAuthorize(READ_ROLES)
    public String print(@PathVariable("id") Integer id, @AuthenticationPrincipal AuthUser actor) {
        requireOwnInvoice(actor, id);
        return service.printableHtml(id);
    }

    // UC 80, 81
    @PostMapping("/{id}/send-email")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    public NotificationResponse sendEmail(@PathVariable("id") Integer id) {
        return service.sendEmail(id);
    }

    @PostMapping("/{id}/send-app")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    public NotificationResponse sendApp(@PathVariable("id") Integer id) {
        return service.sendToApp(id);
    }

    @GetMapping("/{id}/notifications")
    @PreAuthorize(READ_ROLES)
    public List<NotificationResponse> notifications(@PathVariable("id") Integer id,
                                                    @AuthenticationPrincipal AuthUser actor) {
        requireOwnInvoice(actor, id);
        return service.notifications(id);
    }

    @PostMapping("/{id}/share-link")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    public ShareLink shareLink(@PathVariable Integer id) {
        return service.shareLink(id);
    }

    @DeleteMapping("/{id}/share-link")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShareLink(@PathVariable Integer id) {
        service.revokeShareLink(id);
    }

    @GetMapping("/share/{token}")
    public ResponseEntity<SharedInvoice> shared(@PathVariable("token") String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(service.detailByShareToken(token));
    }

    // UC 83
    @PutMapping("/{id}/total")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<InvoiceResponse> adjust(@PathVariable("id") Integer id, @Valid @RequestBody AdjustInvoiceRequest req,
            @AuthenticationPrincipal AuthUser actor, HttpServletRequest http) {
        return ApiResponse.ok("Đã điều chỉnh hóa đơn",
                service.adjustTotal(id, req, CurrentUser.requireUserId(actor), http.getRemoteAddr()));
    }

    // UC 76
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    public List<MonthSummary> summary(@RequestParam("propertyId") Integer propertyId) {
        return service.summaryByMonth(propertyId);
    }

    @GetMapping("/summary/{monthYear}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    public MonthDetailSummary summaryOfMonth(@PathVariable("monthYear") String monthYear,
                                             @RequestParam("propertyId") Integer propertyId) {
        return service.summaryOfMonth(propertyId, monthYear);
    }
}
