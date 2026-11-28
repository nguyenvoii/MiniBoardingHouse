package com.group7.minihouse.phi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.minihouse.shared.exception.BusinessException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Goi API eOTP (https://eotp.com.vn/docs/): eOTP TU SINH ma va gui qua Zalo/SMS, app chi giu request_id roi hoi lai
 * "ma nay dung khong". Chi goi tu server (khoa X-Api-Secret khong duoc lo ra trinh duyet).
 * Bat bang app.otp.provider=eotp; mac dinh tat (app tu sinh ma va gui email).
 */
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "eotp")
public class EotpClient {

    public enum Verdict { OK, WRONG_CODE, EXPIRED, TOO_MANY, NOT_FOUND }

    public record Sent(String requestId, int expiresInSeconds) {}

    private record Raw(int status, String body) {}

    private static final Logger log = LoggerFactory.getLogger(EotpClient.class);
    // Tu doc chuoi bang Jackson 2: converter mac dinh cua Spring 7 la Jackson 3, khong doc duoc JsonNode cua Jackson 2.
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;
    private final String channel;

    @Autowired
    public EotpClient(@Value("${app.otp.eotp.base-url:https://eotp.com.vn/api/v1}") String baseUrl,
                      @Value("${app.otp.eotp.api-key:}") String apiKey,
                      @Value("${app.otp.eotp.api-secret:}") String apiSecret,
                      @Value("${app.otp.eotp.channel:zalo}") String channel) {
        this(configured(baseUrl, apiKey, apiSecret), channel);
    }

    EotpClient(RestClient client, String channel) {
        this.client = client;
        this.channel = channel;
    }

    private static RestClient configured(String baseUrl, String apiKey, String apiSecret) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException(
                    "Thiếu cấu hình app.otp.eotp.api-key / app.otp.eotp.api-secret (đang bật app.otp.provider=eotp)");
        }
        var requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requests.setReadTimeout(Duration.ofSeconds(40)); // eOTP khuyen timeout tu 35 giay tro len cho /otp/send
        return RestClient.builder().baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey).defaultHeader("X-Api-Secret", apiSecret)
                .requestFactory(requests).build();
    }

    /** Gui ma toi so dien thoai. Khong tu goi lai khi chua co phan hoi (tranh nguoi dung nhan 2 ma). */
    public Sent send(String phone) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("phone", phone);
        if (!phone.startsWith("+")) {
            body.put("country_code", "84"); // so noi dia 09xx... can kem ma nuoc
        }
        body.put("channel", channel);
        Raw raw = post("/otp/send", body);
        JsonNode j = parse(raw);
        String requestId = j.path("request_id").asText("");
        if (raw.status() == 200 && j.path("ok").asBoolean(false) && !requestId.isBlank()) {
            return new Sent(requestId, j.path("expires_in").asInt(300));
        }
        String error = j.path("error").asText("unknown");
        log.warn("eOTP /otp/send loi: HTTP {} error={}", raw.status(), error); // khong ghi so dien thoai, khong ghi khoa
        if ("invalid_phone".equals(error)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        throw new BusinessException(HttpStatus.BAD_GATEWAY, "Không gửi được mã xác thực qua Zalo, vui lòng thử lại sau");
    }

    /** Kiem tra ma. Moi ma chi dung duoc 1 lan; sai 5 lan eOTP huy ma. */
    public Verdict verify(String requestId, String code) {
        if (code == null || code.isBlank()) {
            return Verdict.WRONG_CODE;
        }
        Raw raw = post("/otp/verify", Map.of("request_id", requestId, "code", code.trim()));
        JsonNode j = parse(raw);
        if (raw.status() == 200 && j.path("ok").asBoolean(false) && j.path("verified").asBoolean(false)) {
            return Verdict.OK;
        }
        String error = j.path("error").asText("unknown");
        switch (error) {
            case "wrong_code": return Verdict.WRONG_CODE;
            case "expired": return Verdict.EXPIRED;
            case "too_many_attempts": return Verdict.TOO_MANY;
            case "not_found": return Verdict.NOT_FOUND;
            default:
                log.warn("eOTP /otp/verify loi: HTTP {} error={}", raw.status(), error);
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "Không xác thực được mã lúc này, vui lòng thử lại sau");
        }
    }

    private Raw post(String path, Object body) {
        // Tu chuyen sang chuoi JSON: de Content-Length ro rang (RestClient gui Map theo kieu chunked, mot so may chu khong doc duoc)
        // va khong phu thuoc bo chuyen doi JSON mac dinh cua Spring.
        String payload;
        try {
            payload = JSON.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(payload)
                    .exchange((request, response) ->
                            new Raw(response.getStatusCode().value(), StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8)));
        } catch (RestClientException ex) {
            log.warn("Khong goi duoc eOTP {}: {}", path, ex.getClass().getSimpleName());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Không kết nối được dịch vụ gửi mã, vui lòng thử lại sau");
        }
    }

    private static JsonNode parse(Raw raw) {
        try {
            return JSON.readTree(raw.body() == null || raw.body().isBlank() ? "{}" : raw.body());
        } catch (JsonProcessingException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Dịch vụ gửi mã trả về dữ liệu không hợp lệ");
        }
    }
}
