package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.entity.NotificationTemplate;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.NotificationDtos.TemplatePreview;
import com.group7.minihouse.viet.dto.NotificationDtos.TemplateRequest;
import com.group7.minihouse.viet.dto.NotificationDtos.TemplateResponse;
import com.group7.minihouse.viet.repository.NotificationTemplateRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC121: quan ly mau thong bao va kenh gui (EMAIL, SMS, SYSTEM_APP = website/ung dung).
 * Mot ma mau co the co moi kenh 1 mau (rang buoc UNIQUE template_code + channel).
 * Khong xoa mau (user_notifications co the dang tham chieu) ma chi tat/bat.
 */
@Service
public class NotificationTemplateService {

    static final Set<String> CHANNELS = Set.of("EMAIL", "SMS", "SYSTEM_APP");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{2,50}");
    private static final int SUBJECT_MAX = 200;

    private final NotificationTemplateRepository repository;

    public NotificationTemplateService(NotificationTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> search(String code, String channel, Boolean active) {
        String c = VietSupport.blankToNull(code);
        String ch = VietSupport.enumOrNull(channel, CHANNELS, "channel");
        return repository.search(c == null ? null : c.toUpperCase(), ch, active).stream()
                .map(TemplateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(Integer id) {
        return TemplateResponse.from(find(id));
    }

    @Transactional
    public TemplateResponse create(TemplateRequest req) {
        if (req == null) {
            throw new BusinessException("Thiếu dữ liệu");
        }
        String code = VietSupport.requireText(req.templateCode(), "mã mẫu").toUpperCase();
        if (!CODE.matcher(code).matches()) {
            throw new BusinessException("Mã mẫu chỉ gồm chữ hoa, số, dấu gạch dưới (2-50 ký tự)");
        }
        String channel = VietSupport.enumOrNull(req.channel(), CHANNELS, "channel");
        if (channel == null) {
            throw new BusinessException("Thiếu channel (EMAIL, SMS hoặc SYSTEM_APP)");
        }
        if (repository.findByCodeAndChannel(code, channel).isPresent()) {
            throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT,
                    "Mẫu \"" + code + "\" cho kênh " + channel + " đã tồn tại");
        }
        NotificationTemplate t = new NotificationTemplate();
        t.setTemplateCode(code);
        t.setChannel(channel);
        apply(t, req);
        t.setIsActive(true);
        return TemplateResponse.from(repository.save(t));
    }

    /** Khong doi ma mau va kenh (cac thong bao da gui tham chieu theo cap nay). */
    @Transactional
    public TemplateResponse update(Integer id, TemplateRequest req) {
        if (req == null) {
            throw new BusinessException("Thiếu dữ liệu");
        }
        NotificationTemplate t = find(id);
        apply(t, req);
        return TemplateResponse.from(repository.save(t));
    }

    @Transactional
    public TemplateResponse setActive(Integer id, Boolean active) {
        if (active == null) {
            throw new BusinessException("Thiếu active (true/false)");
        }
        NotificationTemplate t = find(id);
        t.setIsActive(active);
        return TemplateResponse.from(repository.save(t));
    }

    /** Xem truoc mau voi gia tri bien do nguoi dung nhap. */
    @Transactional(readOnly = true)
    public TemplatePreview preview(Integer id, Map<String, String> params) {
        NotificationTemplate t = find(id);
        return new TemplatePreview(NotificationSender.render(t.getSubject(), params),
                NotificationSender.render(t.getContent(), params));
    }

    private static void apply(NotificationTemplate t, TemplateRequest req) {
        String subject = VietSupport.blankToNull(req.subject());
        if (subject != null && subject.length() > SUBJECT_MAX) {
            throw new BusinessException("Tiêu đề tối đa " + SUBJECT_MAX + " ký tự");
        }
        t.setSubject(subject);
        t.setContent(VietSupport.requireText(req.content(), "noi dung mau"));
    }

    private NotificationTemplate find(Integer id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.notFound("Mẫu thông báo"));
    }
}
