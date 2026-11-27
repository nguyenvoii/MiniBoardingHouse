package com.group7.minihouse.shared.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group7.minihouse.phi.controller.CashierPaymentController;
import com.group7.minihouse.phi.service.PaymentService;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.storage.FileStorage;
import com.group7.minihouse.toan.controller.MeterReadingController;
import com.group7.minihouse.toan.service.MeterReadingService;
import com.group7.minihouse.shared.security.JwtService;
import com.group7.minihouse.shared.security.UserStatusChecker;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Luat URL khi app.security.enabled=true (khong can DB): mac dinh chi nhan vien duoc vao,
 * nguoi thue chi vao /api/me/** va /api/auth/** => dong doi quen @PreAuthorize cung khong lo du lieu cho nguoi thue.
 */
@ImportAutoConfiguration(Jackson2AutoConfiguration.class)
@WebMvcTest(controllers = {SecurityRulesTest.Probe.class, MeterReadingController.class, CashierPaymentController.class})
@Import({SecurityConfig.class, MethodSecurityConfig.class, JwtService.class, SecurityRulesTest.Probe.class})
@TestPropertySource(properties = {
        "app.security.enabled=true",
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234567890"})
class SecurityRulesTest {

    @RestController
    static class Probe {
        @GetMapping("/api/probe/staff") String staff() { return "ok"; }
        @GetMapping("/api/me/probe") String tenantArea() { return "ok"; }
        @GetMapping("/api/auth/me") String auth() { return "ok"; }
        @GetMapping("/api/invoices/5/detail") String invoiceDetail() { return "ok"; }
        @GetMapping("/api/invoices/5") String invoice() { return "ok"; }
        @org.springframework.web.bind.annotation.PostMapping("/api/auth/forgot-password") String forgot() { return "ok"; }
        @org.springframework.web.bind.annotation.PostMapping("/api/auth/reset-password") String reset() { return "ok"; }
        @org.springframework.web.bind.annotation.PostMapping("/api/auth/verify-reset-code") String verifyReset() { return "ok"; }
        @org.springframework.web.bind.annotation.PostMapping("/api/auth/qr-login") String qr() { return "ok"; }
        @org.springframework.web.bind.annotation.PostMapping("/api/auth/register") String register() { return "ok"; }
        @org.springframework.web.bind.annotation.PostMapping("/api/auth/verify-email") String verify() { return "ok"; }
        @org.springframework.web.bind.annotation.PostMapping("/api/auth/resend-verification") String resend() { return "ok"; }
    }

    /** Dang ky chu tro / xac thuc email / yeu cau thue cua khach vang lai KHONG duoc mo ra ngoai: chua dang nhap la 401. */
    @Test
    void registerVerifyAndGuestBookingAreNotPublic() throws Exception {
        for (String path : List.of("/api/auth/register", "/api/auth/verify-email", "/api/auth/resend-verification", "/api/public/booking")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path))
                    .andExpect(status().isUnauthorized());
        }
    }

    /** UC4: nguoi quen mat khau chua dang nhap duoc, nen 2 API nay phai cong khai; cac API /api/auth khac thi khong. */
    @Test
    void forgotAndResetPasswordArePublic() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/forgot-password"))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/verify-reset-code"))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/reset-password"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    /** Khach quet QR chua co phien dang nhap nen API doi ma QR phai cong khai (tu kiem tra ma ben trong). */
    @Test
    void qrLoginIsPublic() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/qr-login"))
                .andExpect(status().isOk());
    }

    /** Nguoi thue xem chi tiet hoa don duoc (controller kiem chu so huu), nhung khong vao cac URL hoa don khac. */
    @Test
    void tenantMayOpenInvoiceDetailOnly() throws Exception {
        dbRoles = List.of("TENANT");
        String t = bearer("TENANT");
        mvc.perform(get("/api/invoices/5/detail").header("Authorization", t)).andExpect(status().isOk());
        mvc.perform(get("/api/invoices/5").header("Authorization", t)).andExpect(status().isForbidden());
    }

    /** C3: nguoi thue (hoac bat ky ai) da dang nhap khong tu dang ky/xac thuc thanh chu tro duoc: dong ca API. */
    @Test
    void loggedInTenantCannotCallOwnerRegistration() throws Exception {
        dbRoles = List.of("TENANT");
        String t = bearer("TENANT");
        for (String path : List.of("/api/auth/register", "/api/auth/verify-email", "/api/auth/resend-verification")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization", t))
                    .andExpect(status().isForbidden());
        }
    }

    /** T3: dang dung mat khau tam thi chi nhom /api/auth/** dung duoc, con lai 403 du goi API truc tiep. */
    @Test
    void tempPasswordSessionIsLimitedToAuthEndpoints() throws Exception {
        dbRoles = List.of("TENANT");
        mustChange = true;
        String t = bearer("TENANT");
        mvc.perform(get("/api/me/probe").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/invoices/5/detail").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", t)).andExpect(status().isOk());
        mustChange = false; // doi xong mat khau: dung binh thuong
        mvc.perform(get("/api/me/probe").header("Authorization", t)).andExpect(status().isOk());
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean UserStatusChecker statusChecker;
    @MockitoBean MeterReadingService meterReadingService;
    @MockitoBean PaymentService paymentService;
    @MockitoBean FileStorage storage;

    /** Vai tro "trong DB" ma statusChecker tra ve; bearer() dat bang vai tro trong token (nhu chua bi doi). */
    List<String> dbRoles = List.of();
    boolean mustChange = false;

    @BeforeEach
    void databaseKnowsTheUser() {
        when(statusChecker.state(any(), any())).thenAnswer(inv -> Optional.of(new UserStatusChecker.State(dbRoles, mustChange, "test-hash")));
    }

    /** Quyen lay tu DB moi request, khong lay tu token: giang chuc/doi vai tro co hieu luc NGAY. */
    @Test
    void rolesComeFromTheDatabaseNotFromTheToken() throws Exception {
        String oldToken = bearer("MANAGER");
        dbRoles = List.of("TENANT"); // sau khi cap token, chu tro doi nguoi nay thanh vai tro khac

        mvc.perform(get("/api/probe/staff").header("Authorization", oldToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/probe").header("Authorization", oldToken)).andExpect(status().isOk());

        dbRoles = List.of("MANAGER");
        mvc.perform(get("/api/probe/staff").header("Authorization", oldToken)).andExpect(status().isOk());
    }

    /** UC9: khoa tai khoan phai chan NGAY token da cap (kiem tra trang thai trong DB moi request). */
    @Test
    void lockedUsersTokenIsRejectedImmediately() throws Exception {
        when(statusChecker.state(org.mockito.ArgumentMatchers.eq(1), any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/probe/staff").header("Authorization", bearer("MANAGER")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/probe").header("Authorization", bearer("TENANT")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer("MANAGER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meterAndPaymentReadsFollowRoleMatrix() throws Exception {
        for (var role : List.of("LANDLORD", "MANAGER", "CASHIER", "METER_READER", "TENANT")) {
            String token = bearer(role);
            boolean manager = List.of("LANDLORD", "MANAGER").contains(role);
            int meterStatus = manager || "METER_READER".equals(role) ? 200 : 403;
            int paymentStatus = manager || "CASHIER".equals(role) ? 200 : 403;
            mvc.perform(get("/api/meter-periods").param("propertyId", "1").header("Authorization", token))
                    .andExpect(status().is(meterStatus));
            mvc.perform(get("/api/payments/1").header("Authorization", token))
                    .andExpect(status().is(paymentStatus));
        }
    }

    private String bearer(String... roles) {
        dbRoles = List.of(roles);
        Integer tenantId = Arrays.asList(roles).contains("TENANT") ? 1 : null;
        return "Bearer " + jwt.generate(new AuthUser(1, "a@b.c", "A", List.of(roles), tenantId), "test-hash");
    }

    @Test
    void tenantTokenCannotReachUnannotatedStaffEndpoint() throws Exception {
        mvc.perform(get("/api/probe/staff").header("Authorization", bearer("TENANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffTokenReachesStaffEndpoint() throws Exception {
        mvc.perform(get("/api/probe/staff").header("Authorization", bearer("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void tenantTokenReachesTenantArea() throws Exception {
        mvc.perform(get("/api/me/probe").header("Authorization", bearer("TENANT")))
                .andExpect(status().isOk());
    }

    @Test
    void staffTokenCannotReachTenantArea() throws Exception {
        mvc.perform(get("/api/me/probe").header("Authorization", bearer("MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyLoggedInUserReachesAuthArea() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer("TENANT")))
                .andExpect(status().isOk());
    }

    /**
     * Giao dien demo (file tinh /ui/**) khong duoc bi chan khi chua dang nhap; API van phai bi chan.
     * Khong ep 404: may nao co thu muc demo-ui (cau hinh local) thi /ui/index.html tra 200, may khong co thi 404;
     * dieu quan trong la KHONG phai 401/403.
     */
    @Test
    void staticUiIsPublicButApiIsNot() throws Exception {
        int ui = mvc.perform(get("/ui/index.html")).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(ui != 401 && ui != 403, "ui bi chan, status = " + ui);
        mvc.perform(get("/api/probe/staff")).andExpect(status().isUnauthorized());
    }

    /** Giao dien Stayly (index.html + /assets/**) phai tai duoc khi chua dang nhap, neu khong thi khong ai dang nhap duoc. */
    @Test
    void staylyFrontendFilesArePublic() throws Exception {
        for (String path : new String[] {"/", "/index.html", "/assets/index-abc.js", "/favicon.ico"}) {
            int s = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertTrue(s != 401 && s != 403, path + " bi chan, status = " + s);
        }
    }

    @Test
    void noTokenGets401() throws Exception {
        mvc.perform(get("/api/probe/staff")).andExpect(status().isUnauthorized());
    }

    /** Go URL API tren trinh duyet (Accept: text/html) khi chua dang nhap => chuyen ve trang dang nhap, khong hien JSON tho. */
    @Test
    void browserNavigationWithoutLoginIsRedirectedToLoginPage() throws Exception {
        mvc.perform(get("/api/probe/staff").accept(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", org.hamcrest.Matchers.endsWith("/ui/index.html")));
    }

    /** Swagger/Postman/fetch (Accept json hoac mac dinh) van nhan 401 JSON nhu cu. */
    @Test
    void apiClientsStillGet401Json() throws Exception {
        mvc.perform(get("/api/probe/staff").accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
