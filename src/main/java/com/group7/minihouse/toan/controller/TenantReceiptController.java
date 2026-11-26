package com.group7.minihouse.toan.controller;

import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.toan.dto.PaymentDtos.TenantReceipt;
import com.group7.minihouse.toan.service.ReceiptService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/receipts")
public class TenantReceiptController {

    private final ReceiptService receipts;

    public TenantReceiptController(ReceiptService receipts) {
        this.receipts = receipts;
    }

    @GetMapping
    public List<TenantReceipt> list(@AuthenticationPrincipal AuthUser actor) {
        return receipts.forTenant(CurrentUser.requireTenantId(actor));
    }

    @GetMapping(value = "/{id}/print", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> print(@PathVariable Integer id, @AuthenticationPrincipal AuthUser actor) {
        String html = receipts.printableHtmlForTenant(CurrentUser.requireTenantId(actor), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(html);
    }
}
