package com.group7.minihouse.shared.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Thong tin don vi chiu trach nhiem hien CONG KHAI tren trang chu va cac trang /privacy /terms /pricing /contact.
 * Lay tu cau hinh (bien moi truong APP_PUBLIC_OWNER_NAME/EMAIL/PHONE/ADDRESS tren server), KHONG ghi vao repo cong khai.
 */
@Component
public class SitePublicInfo {

    private final String name;
    private final String email;
    private final String phone;
    private final String address;

    public SitePublicInfo(@Value("${app.public.owner-name:}") String name,
                          @Value("${app.public.owner-email:}") String email,
                          @Value("${app.public.owner-phone:}") String phone,
                          @Value("${app.public.owner-address:}") String address) {
        this.name = name == null ? "" : name.trim();
        this.email = email == null ? "" : email.trim();
        this.phone = phone == null ? "" : phone.trim();
        this.address = address == null ? "" : address.trim();
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s);
    }

    public String name() {
        return name.isEmpty() ? "Đang cập nhật" : esc(name);
    }

    /** Cac dong lien he (da escape HTML), bo qua muc chua cau hinh. */
    public String contactLines() {
        StringBuilder b = new StringBuilder();
        b.append("<p><strong>Đơn vị chịu trách nhiệm:</strong> ").append(name()).append("</p>");
        if (!email.isEmpty()) {
            b.append("<p><strong>Email:</strong> <a href=\"mailto:").append(esc(email)).append("\">").append(esc(email)).append("</a></p>");
        }
        if (!phone.isEmpty()) {
            b.append("<p><strong>Điện thoại:</strong> <a href=\"tel:").append(esc(phone.replaceAll("[^0-9+]", ""))).append("\">")
                    .append(esc(phone)).append("</a></p>");
        }
        if (!address.isEmpty()) {
            b.append("<p><strong>Địa chỉ:</strong> ").append(esc(address)).append("</p>");
        }
        return b.toString();
    }

    /** Mot dong gon cho chan trang. */
    public String contactInline() {
        StringBuilder b = new StringBuilder("Đơn vị chịu trách nhiệm: <strong>").append(name()).append("</strong>");
        if (!email.isEmpty()) {
            b.append(" · Email: <a href=\"mailto:").append(esc(email)).append("\">").append(esc(email)).append("</a>");
        }
        if (!phone.isEmpty()) {
            b.append(" · ĐT: ").append(esc(phone));
        }
        if (!address.isEmpty()) {
            b.append(" · ").append(esc(address));
        }
        return b.toString();
    }
}
