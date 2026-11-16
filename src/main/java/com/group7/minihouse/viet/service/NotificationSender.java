package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.entity.NotificationTemplate;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserNotification;
import com.group7.minihouse.viet.repository.NotificationTemplateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gui thong bao vao trung tam thong bao (kenh SYSTEM_APP) cho cac chuc nang khac cua Viet goi
 * (nhac no, hop dong sap het han, dieu chinh hoa don...). Neu co mau dang bat (UC121) thi dung mau,
 * nguoc lai dung noi dung mac dinh do noi goi truyen vao. Mau dung bien dang {{ten_bien}}.
 * Kenh EMAIL/SMS chi luu mau; viec gui that phu thuoc util gui email/OTP cua Phi nen chua noi vao day.
 */
@Service
public class NotificationSender {

    public static final String CHANNEL = "SYSTEM_APP";
    private static final int TITLE_MAX = 200;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    @PersistenceContext
    private EntityManager em;

    private final NotificationTemplateRepository templateRepository;

    public NotificationSender(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /** Thay {{bien}} bang gia tri; bien khong co gia tri duoc giu nguyen de de phat hien sai sot. */
    public static String render(String text, Map<String, String> params) {
        if (text == null) {
            return "";
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = params == null ? null : params.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Gui cho tai khoan cua khach thue (tai khoan noi voi khach qua so dien thoai). false = khach chua co tai khoan. */
    @Transactional
    public boolean notifyTenant(Integer tenantId, String templateCode, String defaultTitle, String defaultContent,
                                Map<String, String> params, String referenceType, Integer referenceId) {
        Tenant tenant = tenantId == null ? null : em.find(Tenant.class, tenantId);
        if (tenant == null || tenant.getPhoneNumber() == null || tenant.getPhoneNumber().isBlank()) {
            return false;
        }
        List<User> users = em.createQuery("select u from User u where u.phoneNumber = :p and u.status = 'ACTIVE'",
                User.class).setParameter("p", tenant.getPhoneNumber().trim()).getResultList();
        if (users.isEmpty()) {
            return false;
        }
        deliver(users.get(0), templateCode, defaultTitle, defaultContent, params, referenceType, referenceId);
        return true;
    }

    /** Gui cho moi nhan vien dang hoat dong co mot trong cac vai tro (vi du LANDLORD, MANAGER). */
    @Transactional
    public int notifyStaff(Collection<String> roleCodes, String templateCode, String defaultTitle,
                           String defaultContent, Map<String, String> params, String referenceType,
                           Integer referenceId) {
        List<User> users = em.createQuery("select distinct ur.user from UserRole ur"
                + " where ur.role.roleCode in :codes and ur.user.status = 'ACTIVE'", User.class)
                .setParameter("codes", roleCodes).getResultList();
        for (User u : users) {
            deliver(u, templateCode, defaultTitle, defaultContent, params, referenceType, referenceId);
        }
        return users.size();
    }

    private void deliver(User user, String templateCode, String defaultTitle, String defaultContent,
                         Map<String, String> params, String referenceType, Integer referenceId) {
        NotificationTemplate tpl = templateRepository.findActive(templateCode, CHANNEL).orElse(null);
        String title = tpl != null && tpl.getSubject() != null && !tpl.getSubject().isBlank()
                ? render(tpl.getSubject(), params) : render(defaultTitle, params);
        String content = tpl != null ? render(tpl.getContent(), params) : render(defaultContent, params);
        UserNotification n = new UserNotification();
        n.setUser(user);
        n.setNotifTemplate(tpl);
        n.setTitle(title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title);
        n.setContent(content);
        n.setReferenceType(referenceType);
        n.setReferenceId(referenceId);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        em.persist(n);
    }
}
