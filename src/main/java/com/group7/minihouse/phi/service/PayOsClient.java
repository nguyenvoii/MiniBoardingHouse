package com.group7.minihouse.phi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.group7.minihouse.shared.exception.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.payment.payos.enabled", havingValue = "true")
public class PayOsClient {

    private final RestClient client;
    private final String checksumKey;
    private final String returnUrl;
    private final String cancelUrl;
    private final String checkoutHost;

    @Autowired
    public PayOsClient(@Value("${app.payment.payos.client-id}") String clientId,
                       @Value("${app.payment.payos.api-key}") String apiKey,
                       @Value("${app.payment.payos.checksum-key}") String checksumKey,
                       @Value("${app.payment.payos.return-url}") String returnUrl,
                       @Value("${app.payment.payos.cancel-url}") String cancelUrl,
                       @Value("${app.payment.payos.base-url:https://api-merchant.payos.vn}") String baseUrl,
                       @Value("${app.payment.payos.checkout-host:pay.payos.vn}") String checkoutHost) {
        this(configuredClient(clientId, apiKey, baseUrl), checksumKey, returnUrl, cancelUrl, checkoutHost);
    }

    PayOsClient(RestClient client, String checksumKey, String returnUrl, String cancelUrl) {
        this(client, checksumKey, returnUrl, cancelUrl, "pay.payos.vn");
    }

    PayOsClient(RestClient client, String checksumKey, String returnUrl, String cancelUrl, String checkoutHost) {
        if (checksumKey.isBlank()) {
            throw new IllegalArgumentException("app.payment.payos.checksum-key is required");
        }
        this.client = client;
        this.checksumKey = checksumKey;
        this.returnUrl = redirectUrl(returnUrl);
        this.cancelUrl = redirectUrl(cancelUrl);
        this.checkoutHost = checkoutHost;
    }

    private static RestClient configuredClient(String clientId, String apiKey, String baseUrl) {
        var requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        requests.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-client-id", clientId)
                .defaultHeader("x-api-key", apiKey)
                .requestFactory(requests)
                .build();
    }

    public Checkout create(long order, long amount) {
        String description = "MBH" + order;
        String signature = sign(checksumKey, order, amount, description, returnUrl, cancelUrl);
        try {
            JsonNode payload = client.post().uri("/v2/payment-requests").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("orderCode", order, "amount", amount, "description", description,
                            "returnUrl", returnUrl, "cancelUrl", cancelUrl, "signature", signature))
                    .retrieve().body(JsonNode.class);
            JsonNode data = result(payload, order, amount);
            URI checkout = URI.create(data.path("checkoutUrl").asText());
            if (!"https".equals(checkout.getScheme()) || !checkoutHost.equalsIgnoreCase(checkout.getHost())
                    || checkout.getUserInfo() != null) {
                throw upstreamFailure();
            }
            return new Checkout(order, checkout.toString());
        } catch (RestClientException | IllegalArgumentException ex) {
            throw upstreamFailure();
        }
    }

    public boolean paid(long order, long amount) {
        try {
            JsonNode payload = client.get().uri("/v2/payment-requests/{order}", order)
                    .retrieve().body(JsonNode.class);
            JsonNode data = result(payload, order, amount);
            return "PAID".equals(data.path("status").asText()) && matchesInteger(data.path("amountPaid"), amount);
        } catch (RestClientException ex) {
            throw upstreamFailure();
        }
    }

    private static JsonNode result(JsonNode response, long order, long amount) {
        if (response == null || !"00".equals(response.path("code").asText())) {
            throw upstreamFailure();
        }
        JsonNode data = response.path("data");
        if (!matchesInteger(data.path("orderCode"), order) || !matchesInteger(data.path("amount"), amount)) {
            throw upstreamFailure();
        }
        return data;
    }

    private static boolean matchesInteger(JsonNode field, long expected) {
        return field.isIntegralNumber() && field.canConvertToLong() && field.longValue() == expected;
    }

    private static String redirectUrl(String input) {
        URI url = URI.create(input);
        if (!"https".equals(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null
                || input.indexOf('&') >= 0) {
            throw new IllegalArgumentException("Invalid payOS return/cancel URL");
        }
        return url.toString();
    }

    static String sign(String key, long order, long amount, String description, String returnUrl, String cancelUrl) {
        String value = "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description
                + "&orderCode=" + order + "&returnUrl=" + returnUrl;
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot sign payOS payment", ex);
        }
    }

    private static BusinessException upstreamFailure() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "Khong the xac minh phan hoi tu cong thanh toan payOS");
    }

    public record Checkout(long orderCode, String checkoutUrl) {}
}
