package com.group7.minihouse.shared.notification;

import com.group7.minihouse.shared.exception.BusinessException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.sms.enabled", havingValue = "true")
public class TwilioSmsSender {

    private final RestClient client;
    private final String from;

    public TwilioSmsSender(@Value("${app.sms.twilio.account-sid}") String accountSid,
                           @Value("${app.sms.twilio.auth-token}") String authToken,
                           @Value("${app.sms.twilio.from}") String from) {
        var requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01/Accounts/" + accountSid)
                .defaultHeaders(headers -> headers.setBasicAuth(accountSid, authToken))
                .requestFactory(requests)
                .build();
        this.from = from;
    }

    public void send(String recipient, String body) {
        String number = recipient.trim().replaceAll("[ .()-]", "");
        if (number.matches("0[0-9]{9}")) {
            number = "+84" + number.substring(1);
        }
        if (!number.matches("[+][1-9][0-9]{7,14}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("From", from);
        form.add("To", number);
        form.add("Body", body);
        try {
            client.post().uri("/Messages.json").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Tạm thời không gửi được SMS");
        }
    }
}
