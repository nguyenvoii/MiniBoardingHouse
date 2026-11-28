package com.group7.minihouse.shared.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.minihouse.shared.exception.BusinessException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gui SMS qua SpeedSMS (https://speedsms.vn/sms-api-service/): GET {base}/sms/send?access-token=&to=&content=&type=&sender=.
 * type 4 = brandname co san cua SpeedSMS (Verify/Notify, khong phai dang ky brandname), 2 = so gui ngau nhien, 3 = brandname rieng.
 * Bat bang app.sms.speedsms.enabled=true + app.sms.speedsms.access-token. Access-token nam trong URL theo tai lieu cua ho,
 * nen KHONG bao gio ghi URL/thong bao loi cua RestClient ra log.
 */
@Component
@ConditionalOnProperty(name = "app.sms.speedsms.enabled", havingValue = "true")
@Order(1)
public class SpeedSmsSender implements SmsSender {

    private record Raw(int status, String body) {}

    private static final Logger log = LoggerFactory.getLogger(SpeedSmsSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;
    private final String baseUrl;
    private final String accessToken;
    private final String type;
    private final String sender;

    @Autowired
    public SpeedSmsSender(@Value("${app.sms.speedsms.access-token:}") String accessToken,
                          @Value("${app.sms.speedsms.type:4}") String type,
                          @Value("${app.sms.speedsms.sender:}") String sender,
                          @Value("${app.sms.speedsms.base-url:https://api.speedsms.vn/index.php}") String baseUrl) {
        this(configured(accessToken), baseUrl, accessToken, type, sender);
    }

    SpeedSmsSender(RestClient client, String baseUrl, String accessToken, String type, String sender) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
        this.type = type;
        this.sender = sender == null ? "" : sender.trim();
    }

    private static RestClient configured(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Thiếu cấu hình app.sms.speedsms.access-token (đang bật app.sms.speedsms.enabled=true)");
        }
        var requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requests.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requests).build();
    }

    @Override
    public void send(String recipient, String body) {
        String raw0 = recipient.trim().replaceAll("[ .()-]", "");
        String number; // SpeedSMS nhan so Viet Nam dang 84xxxxxxxxx
        if (raw0.matches("[+]84[0-9]{9}")) {
            number = raw0.substring(1);
        } else if (raw0.matches("0[0-9]{9}")) {
            number = "84" + raw0.substring(1);
        } else if (raw0.matches("84[0-9]{9}")) {
            number = raw0;
        } else {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        StringBuilder q = new StringBuilder(baseUrl).append("/sms/send?access-token=").append(enc(accessToken))
                .append("&to=").append(number).append("&content=").append(enc(body)).append("&type=").append(enc(type));
        if (!sender.isEmpty()) {
            q.append("&sender=").append(enc(sender));
        }
        Raw raw = get(URI.create(q.toString()));
        JsonNode j;
        try {
            j = JSON.readTree(raw.body() == null || raw.body().isBlank() ? "{}" : raw.body());
        } catch (JsonProcessingException ex) {
            throw unavailable("phan hoi khong phai JSON, HTTP " + raw.status());
        }
        if (raw.status() == 200 && "success".equals(j.path("status").asText())) {
            return;
        }
        String code = j.path("code").asText("?");
        if ("105".equals(code)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        // 300 = het tien, 007/008/009 = IP/tai khoan bi khoa, 101 = tham so, 110 = ma hoa noi dung, 113 = noi dung qua dai
        throw unavailable("HTTP " + raw.status() + " code=" + code);
    }

    private Raw get(URI uri) {
        try {
            return client.get().uri(uri).exchange((request, response) ->
                    new Raw(response.getStatusCode().value(), StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8)));
        } catch (RestClientException ex) {
            throw unavailable(ex.getClass().getSimpleName()); // khong ghi message: co chua URL kem access-token
        }
    }

    private static BusinessException unavailable(String detail) {
        log.warn("SpeedSMS khong gui duoc: {}", detail);
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Tạm thời không gửi được SMS");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
