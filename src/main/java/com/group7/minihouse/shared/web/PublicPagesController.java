package com.group7.minihouse.shared.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Cac trang cong khai (khong can dang nhap) cho nguoi dung va don vi duyet dich vu ben thu ba (vd eOTP):
 * /privacy, /terms, /pricing, /contact, va trang chu (/) kem chan trang ghi don vi chiu trach nhiem + lien ket phap ly.
 * Chan trang duoc chen thang vao HTML tra ve de bot khong chay JavaScript cung doc duoc.
 */
@Controller
public class PublicPagesController {

    private static final String OWNER_SLOT = "<!--stayly:site-owner-->";

    private static final String[][] NAV = {
            {"/privacy", "Chính sách bảo mật"}, {"/terms", "Điều khoản sử dụng"},
            {"/pricing", "Bảng giá và hoàn tiền"}, {"/contact", "Liên hệ"}};

    private static final String LAYOUT = """
            <!doctype html>
            <html lang="vi"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>{{title}} | Stayly</title>
            <meta name="description" content="{{title}} của Stayly, phần mềm quản lý nhà trọ.">
            <style>
            body{margin:0;background:#f1f5f1;color:#14231c;font:16px/1.65 'Segoe UI',system-ui,-apple-system,Roboto,Arial,sans-serif}
            header{background:#0e6b4a;color:#fff;padding:14px 20px}
            header a{color:#fff;text-decoration:none;font-weight:700;font-size:20px}
            nav{max-width:780px;margin:0 auto;padding:12px 20px 0;font-size:14px}
            nav a{color:#0e6b4a;margin-right:14px}
            main{max-width:780px;margin:12px auto;background:#fff;border-radius:12px;padding:8px 28px 24px}
            h1{font-size:26px;margin:20px 0 4px}h2{font-size:19px;margin:24px 0 4px;color:#0e6b4a}
            p,li{color:#2d3f35}a{color:#0e6b4a}
            .note{font-size:13px;color:#596b61}
            footer{max-width:780px;margin:0 auto;padding:8px 20px 32px;font-size:13px;color:#596b61;text-align:center}
            </style></head><body>
            <header><a href="/">Stayly</a></header>
            <nav>{{nav}}</nav>
            <main>{{body}}</main>
            <footer>{{footer}}</footer>
            </body></html>
            """;

    private final Map<String, String> pages = new LinkedHashMap<>();
    private final String home;

    public PublicPagesController(SitePublicInfo info, @Value("classpath:static/index.html") Resource index) throws IOException {
        String nav = navLinks();
        String footer = info.contactInline() + "<br>" + navLinks();
        for (String[] n : NAV) {
            String name = n[0].substring(1);
            String body = read(new ClassPathResource("public-pages/" + name + ".html"))
                    .replace("{{contact}}", info.contactLines());
            pages.put(n[0], LAYOUT.replace("{{title}}", n[1]).replace("{{nav}}", nav)
                    .replace("{{body}}", body).replace("{{footer}}", footer));
        }
        this.home = index.exists() ? injectFooter(read(index), info) : null;
    }

    private static String navLinks() {
        StringBuilder b = new StringBuilder();
        for (String[] n : NAV) {
            b.append("<a href=\"").append(n[0]).append("\">").append(n[1]).append("</a> ");
        }
        return b.toString().trim();
    }

    private static String injectFooter(String html, SitePublicInfo info) {
        if (html.contains(OWNER_SLOT)) {
            return html.replace(OWNER_SLOT, info.contactInline());
        }
        String footer = "<footer id=\"site-legal\" style=\"max-width:780px;margin:0 auto;padding:16px 20px 28px;"
                + "font:13px/1.7 'Segoe UI',system-ui,Arial,sans-serif;color:#596b61;text-align:center\">"
                + "<div>Stayly là phần mềm quản lý nhà trọ: phòng, khách thuê, hợp đồng, điện nước, hóa đơn và thu tiền.</div>"
                + "<div>" + info.contactInline() + "</div>"
                + "<div>" + navLinks() + "</div></footer>";
        int i = html.lastIndexOf("</body>");
        return i < 0 ? html + footer : html.substring(0, i) + footer + html.substring(i);
    }

    private static String read(Resource r) throws IOException {
        try (var in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache()).body(body);
    }

    @GetMapping("/privacy")
    public ResponseEntity<String> privacy() { return html(pages.get("/privacy")); }

    @GetMapping("/terms")
    public ResponseEntity<String> terms() { return html(pages.get("/terms")); }

    @GetMapping("/pricing")
    public ResponseEntity<String> pricing() { return html(pages.get("/pricing")); }

    @GetMapping("/contact")
    public ResponseEntity<String> contact() { return html(pages.get("/contact")); }

    /** Trang chu = giao dien (SPA) kem chan trang cong khai; chua build giao dien thi 404. */
    @GetMapping({"/", "/index.html"})
    public ResponseEntity<String> homePage() {
        return home == null ? ResponseEntity.notFound().build() : html(home);
    }
}
