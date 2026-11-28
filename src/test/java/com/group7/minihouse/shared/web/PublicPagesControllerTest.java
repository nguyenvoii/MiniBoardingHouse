package com.group7.minihouse.shared.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Trang phap ly cong khai: co noi dung that, co don vi chiu trach nhiem, escape HTML, chan trang chen vao trang chu. */
class PublicPagesControllerTest {

    private static final Resource INDEX = new ByteArrayResource(
            "<html><head><title>Stayly</title></head><body><div id=\"root\"></div><script src=\"/a.js\"></script></body></html>"
                    .getBytes(StandardCharsets.UTF_8));

    private MockMvc mvc(SitePublicInfo info, Resource index) throws Exception {
        return MockMvcBuilders.standaloneSetup(new PublicPagesController(info, index)).build();
    }

    private static String body(MockMvc mvc, String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void everyLegalPageOpensAndShowsWhoIsResponsible() throws Exception {
        MockMvc mvc = mvc(new SitePublicInfo("Nguyễn Văn A", "lienhe@example.com", "0901 234 567", "Hà Nội"), INDEX);
        String[][] pages = {{"/privacy", "Chính sách bảo mật"}, {"/terms", "Điều khoản sử dụng"},
                {"/pricing", "Bảng giá và chính sách hoàn tiền"}, {"/contact", "Liên hệ"}};
        for (String[] p : pages) {
            String html = body(mvc, p[0]);
            assertTrue(html.contains(p[1]), p[0] + " thieu tieu de");
            assertTrue(html.contains("Nguyễn Văn A"), p[0] + " thieu ten don vi chiu trach nhiem");
            assertTrue(html.contains("lienhe@example.com"), p[0] + " thieu email");
            assertTrue(html.contains("0901 234 567"), p[0] + " thieu dien thoai");
            assertFalse(html.contains("{{"), p[0] + " con sot cho trong mau");
        }
    }

    @Test
    void policyExplainsOtpChannelsAndPricingStatesFreeAndRefund() throws Exception {
        MockMvc mvc = mvc(new SitePublicInfo("A", "", "", ""), INDEX);
        String privacy = body(mvc, "/privacy");
        assertTrue(privacy.contains("Zalo") && privacy.contains("eOTP") && privacy.contains("Brevo") && privacy.contains("SePay"));
        String pricing = body(mvc, "/pricing");
        assertTrue(pricing.contains("Miễn phí") && pricing.contains("hoàn tiền"));
    }

    @Test
    void ownerValuesAreHtmlEscaped() throws Exception {
        String html = body(mvc(new SitePublicInfo("<script>alert(1)</script>", "a@b.c", "", ""), INDEX), "/contact");
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void unconfiguredOwnerShowsAClearPlaceholderNotAnEmptyPage() throws Exception {
        String html = body(mvc(new SitePublicInfo("", null, null, null), INDEX), "/privacy");
        assertTrue(html.contains("Đơn vị chịu trách nhiệm") && html.contains("Đang cập nhật"));
    }

    @Test
    void homePageKeepsTheSpaAndGetsTheLegalFooterForBotsWithoutJavascript() throws Exception {
        MockMvc mvc = mvc(new SitePublicInfo("Nguyễn Văn A", "lienhe@example.com", "", ""), INDEX);
        for (String path : new String[] {"/", "/index.html"}) {
            String html = body(mvc, path);
            assertTrue(html.contains("<div id=\"root\"></div>"), "mat giao dien SPA");
            assertTrue(html.contains("id=\"site-legal\"") && html.contains("Nguyễn Văn A") && html.contains("href=\"/privacy\""));
            assertTrue(html.indexOf("site-legal") > html.indexOf("id=\"root\""), "chan trang phai nam sau giao dien");
        }
    }

    @Test
    void homeIsNotFoundWhenTheFrontendWasNotBuilt() throws Exception {
        MockMvc mvc = mvc(new SitePublicInfo("A", "", "", ""), new org.springframework.core.io.ClassPathResource("khong-co.html"));
        mvc.perform(get("/")).andExpect(status().isNotFound());
    }
}
