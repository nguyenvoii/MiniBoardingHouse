package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.ContractDtos.AdjustRequest;
import com.group7.minihouse.phi.dto.ContractDtos.ContractDetail;
import com.group7.minihouse.phi.dto.ContractDtos.ContractResponse;
import com.group7.minihouse.phi.dto.ContractDtos.CreateContractRequest;
import com.group7.minihouse.phi.dto.ContractDtos.ExtendRequest;
import com.group7.minihouse.phi.dto.ContractDtos.PaymentTermsRequest;
import com.group7.minihouse.phi.dto.ContractDtos.PricingRequest;
import com.group7.minihouse.phi.dto.ContractDtos.TerminateRequest;
import com.group7.minihouse.phi.dto.ContractDtos.TransferRequest;
import com.group7.minihouse.phi.dto.ContractDtos.UtilityTermsRequest;
import com.group7.minihouse.phi.service.ContractService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.export.TableExporter;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.shared.storage.FileStorage;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.PathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC41-51: hop dong thue phong. Nhan vien (LANDLORD, MANAGER) quan ly; xem hop dong cua minh qua /api/me/contracts
 * (TENANT). @PreAuthorize chi co tac dung khi app.security.enabled=true.
 */
@RestController
public class ContractController {

    private static final String STAFF = "hasAnyRole('LANDLORD','MANAGER')";

    private final ContractService service;
    private final FileStorage fileStorage;

    public ContractController(ContractService service, FileStorage fileStorage) {
        this.service = service;
        this.fileStorage = fileStorage;
    }

    @PostMapping("/api/contracts")
    @PreAuthorize(STAFF)
    public ApiResponse<ContractResponse> create(@RequestBody CreateContractRequest req,
                                                @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.create(req, actor(user), http.getRemoteAddr()));
    }

    @GetMapping("/api/contracts")
    @PreAuthorize(STAFF)
    public ApiResponse<PageResponse<ContractResponse>> search(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(keyword, status, page, size));
    }

    @GetMapping("/api/contracts/{id}")
    @PreAuthorize(STAFF)
    public ApiResponse<ContractDetail> detail(@PathVariable Integer id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PutMapping("/api/contracts/{id}/pricing")
    @PreAuthorize(STAFF)
    public ApiResponse<ContractResponse> pricing(@PathVariable Integer id, @RequestBody PricingRequest req,
                                                 @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.updatePricing(id, req, actor(user), http.getRemoteAddr()));
    }

    @PutMapping("/api/contracts/{id}/payment-terms")
    @PreAuthorize(STAFF)
    public ApiResponse<ContractResponse> paymentTerms(@PathVariable Integer id,
                                                      @RequestBody PaymentTermsRequest req,
                                                      @AuthenticationPrincipal AuthUser user,
                                                      HttpServletRequest http) {
        return ApiResponse.ok(service.updatePaymentTerms(id, req, actor(user), http.getRemoteAddr()));
    }

    @PutMapping("/api/contracts/{id}/utility-terms")
    @PreAuthorize(STAFF)
    public ApiResponse<ContractResponse> utilityTerms(@PathVariable Integer id,
                                                      @RequestBody UtilityTermsRequest req,
                                                      @AuthenticationPrincipal AuthUser user,
                                                      HttpServletRequest http) {
        return ApiResponse.ok(service.updateUtilityTerms(id, req, actor(user), http.getRemoteAddr()));
    }

    /** UC45 + UC47: tai PDF hop dong (nhan vien). */
    @GetMapping("/api/contracts/{id}/pdf")
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> pdf(@PathVariable Integer id, @AuthenticationPrincipal AuthUser user) {
        return pdfResponse(service.pdf(id, actor(user)), id);
    }

    @PostMapping("/api/contracts/{id}/send")
    @PreAuthorize(STAFF)
    public ApiResponse<Void> send(@PathVariable Integer id, @AuthenticationPrincipal AuthUser user,
                                  HttpServletRequest http) {
        return ApiResponse.ok(service.send(id, actor(user), http.getRemoteAddr()), null);
    }

    /** Nhan vien ky thay (khi khach ky giay tai quay): form-data, truong "file" la anh chu ky (toi da 1 MB). */
    @PostMapping(value = "/api/contracts/{id}/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(STAFF)
    public ApiResponse<ContractResponse> sign(@PathVariable Integer id, @RequestParam("file") MultipartFile file,
                                              @AuthenticationPrincipal AuthUser user, HttpServletRequest http)
            throws IOException {
        return ApiResponse.ok(service.sign(id, file.getBytes(), actor(user), http.getRemoteAddr()));
    }

    @PutMapping("/api/contracts/{id}/extend")
    @PreAuthorize(STAFF)
    public ApiResponse<ContractResponse> extend(@PathVariable Integer id, @RequestBody ExtendRequest req,
                                                @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.extend(id, req, actor(user), http.getRemoteAddr()));
    }

    @PatchMapping("/api/contracts/{id}")
    @PreAuthorize(STAFF)
    public ApiResponse<ContractResponse> adjust(@PathVariable Integer id, @RequestBody AdjustRequest req,
                                                @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.adjust(id, req, actor(user), http.getRemoteAddr()));
    }

    @PostMapping("/api/contracts/{id}/transfer")
    @PreAuthorize(STAFF)
    public ApiResponse<ContractResponse> transfer(@PathVariable Integer id, @RequestBody TransferRequest req,
                                                  @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.transferRoom(id, req, actor(user), http.getRemoteAddr()));
    }

    @PostMapping("/api/contracts/{id}/terminate")
    @PreAuthorize(STAFF)
    public ApiResponse<ContractResponse> terminate(@PathVariable Integer id, @RequestBody TerminateRequest req,
                                                   @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.terminate(id, req, actor(user), http.getRemoteAddr()));
    }

    /** Anh chu ky: cong khai, ten file la UUID ngau nhien. */
    @GetMapping("/api/contracts/signatures/{name}")
    public ResponseEntity<PathResource> signature(@PathVariable String name) {
        return fileStorage.find(ContractService.SIGNATURE_FOLDER, name)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(FileStorage.contentTypeOf(name)))
                        .header("X-Content-Type-Options", "nosniff")
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        .body(new PathResource(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------------ nguoi thue (UC36/37)

    @GetMapping("/api/me/contracts")
    public ApiResponse<List<ContractResponse>> mine(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.mine(CurrentUser.requireTenantId(user)));
    }

    @GetMapping("/api/me/contracts/{id}")
    public ApiResponse<ContractDetail> mineDetail(@PathVariable Integer id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.detailOfTenant(id, CurrentUser.requireTenantId(user)));
    }

    @PostMapping(value = "/api/me/contracts/{id}/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ContractResponse> mineSign(@PathVariable Integer id, @RequestParam("file") MultipartFile file,
                                                  @AuthenticationPrincipal AuthUser user, HttpServletRequest http)
            throws IOException {
        return ApiResponse.ok(service.signByTenant(id, CurrentUser.requireTenantId(user), file.getBytes(),
                CurrentUser.requireUserId(user), http.getRemoteAddr()));
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] data, Integer id) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(TableExporter.PDF_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("hop-dong-" + id + ".pdf").build().toString())
                .body(data);
    }

    /** Tat bao mat (dev) thi khong co nguoi dung: tra null, service bo qua ghi nhat ky va nguoi sua. */
    private static Integer actor(AuthUser user) {
        return user == null ? null : user.userId();
    }
}
