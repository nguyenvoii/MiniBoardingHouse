package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.BankAccountRequest;
import com.group7.minihouse.phi.dto.BankAccountResponse;
import com.group7.minihouse.phi.service.BankAccountService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.shared.storage.FileStorage;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.PathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC119: tai khoan ngan hang va QR nhan tien. Xem: moi nhan vien. Them/sua/xoa: LANDLORD, MANAGER.
 * (@PreAuthorize chi co tac dung khi app.security.enabled=true.)
 */
@RestController
public class BankAccountController {

    private final BankAccountService service;
    private final FileStorage fileStorage;

    public BankAccountController(BankAccountService service, FileStorage fileStorage) {
        this.service = service;
        this.fileStorage = fileStorage;
    }

    @GetMapping("/api/properties/{propertyId}/bank-accounts")
    public ApiResponse<List<BankAccountResponse>> list(@PathVariable Integer propertyId) {
        return ApiResponse.ok(service.list(propertyId));
    }

    @PostMapping("/api/properties/{propertyId}/bank-accounts")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<BankAccountResponse> create(@PathVariable Integer propertyId,
                                                   @RequestBody BankAccountRequest req,
                                                   @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.create(propertyId, req, actor(user), http.getRemoteAddr()));
    }

    @PutMapping("/api/bank-accounts/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<BankAccountResponse> update(@PathVariable Integer id, @RequestBody BankAccountRequest req,
                                                   @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.update(id, req, actor(user), http.getRemoteAddr()));
    }

    @PutMapping("/api/bank-accounts/{id}/default")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<BankAccountResponse> setDefault(@PathVariable Integer id,
                                                       @AuthenticationPrincipal AuthUser user,
                                                       HttpServletRequest http) {
        return ApiResponse.ok(service.setDefault(id, actor(user), http.getRemoteAddr()));
    }

    @DeleteMapping("/api/bank-accounts/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<Void> deactivate(@PathVariable Integer id, @AuthenticationPrincipal AuthUser user,
                                        HttpServletRequest http) {
        service.deactivate(id, actor(user), http.getRemoteAddr());
        return ApiResponse.ok("Da ngung dung tai khoan", null);
    }

    /** Dat anh QR (form-data, truong "file": PNG/JPG/WebP toi da 2 MB). */
    @PutMapping(value = "/api/bank-accounts/{id}/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<BankAccountResponse> setQr(@PathVariable Integer id, @RequestParam("file") MultipartFile file,
                                                  @AuthenticationPrincipal AuthUser user, HttpServletRequest http)
            throws IOException {
        return ApiResponse.ok(service.setQr(id, file.getBytes(), actor(user), http.getRemoteAddr()));
    }

    /** Ảnh QR dùng trong luồng thanh toán sau khi đăng nhập. */
    @GetMapping("/api/bank-accounts/qr/{name}")
    public ResponseEntity<PathResource> qr(@PathVariable String name) {
        return fileStorage.find(BankAccountService.QR_FOLDER, name)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(FileStorage.contentTypeOf(name)))
                        .header("X-Content-Type-Options", "nosniff")
                        .cacheControl(CacheControl.noStore())
                        .body(new PathResource(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Khi tat bao mat (che do dev) khong co token nen user = null: ghi log voi nguoi thuc hien la 0 se loi FK, nen bao ro. */
    private static Integer actor(AuthUser user) {
        return CurrentUser.requireUserId(user);
    }
}
