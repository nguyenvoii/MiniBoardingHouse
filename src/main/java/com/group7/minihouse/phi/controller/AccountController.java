package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.ChangePasswordRequest;
import com.group7.minihouse.phi.dto.ProfileResponse;
import com.group7.minihouse.phi.dto.UpdateProfileRequest;
import com.group7.minihouse.phi.service.AccountService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.group7.minihouse.shared.storage.FileStorage;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.io.PathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC5, UC6: moi nguoi da dang nhap (nhan vien va nguoi thue) thao tac tren tai khoan cua chinh minh. */
@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private final AccountService service;
    private final FileStorage fileStorage;

    public AccountController(AccountService service, FileStorage fileStorage) {
        this.service = service;
        this.fileStorage = fileStorage;
    }

    /** UC7: dat anh dai dien (form-data, truong "file": PNG/JPG/WebP toi da 2 MB). */
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProfileResponse> updateAvatar(@RequestParam("file") MultipartFile file,
                                                     @AuthenticationPrincipal AuthUser user,
                                                     HttpServletRequest http) throws IOException {
        return ApiResponse.ok(service.updateAvatar(CurrentUser.requireUserId(user), file.getBytes(),
                http.getRemoteAddr()));
    }

    @DeleteMapping("/avatar")
    public ApiResponse<ProfileResponse> removeAvatar(@AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.removeAvatar(CurrentUser.requireUserId(user), http.getRemoteAddr()));
    }

    /** Xem anh dai dien. Cong khai (the img khong gui duoc token); ten file la UUID ngau nhien nen kho doan. */
    @GetMapping("/avatars/{name}")
    public ResponseEntity<PathResource> avatar(@PathVariable String name) {
        return fileStorage.find(AccountService.AVATAR_FOLDER, name)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(FileStorage.contentTypeOf(name)))
                        .header("X-Content-Type-Options", "nosniff")
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        .body(new PathResource(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        service.changePassword(CurrentUser.requireUserId(user), req.currentPassword(), req.newPassword(),
                http.getRemoteAddr());
        return ApiResponse.ok("Doi mat khau thanh cong", null);
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> profile(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.getProfile(CurrentUser.requireUserId(user)));
    }

    @PutMapping("/profile")
    public ApiResponse<ProfileResponse> updateProfile(@RequestBody UpdateProfileRequest req,
                                                      @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.updateProfile(CurrentUser.requireUserId(user), req, http.getRemoteAddr()));
    }
}
