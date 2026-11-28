package com.group7.minihouse.shared.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.group7.minihouse.shared.exception.BusinessException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Gui SMS qua Twilio (khong goi mang that). */
class TwilioSmsSenderTest {

    private static final String URL = "https://api.twilio.com/2010-04-01/Accounts/ACtest/Messages.json";
    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://api.twilio.com/2010-04-01/Accounts/ACtest")
            .defaultHeaders(h -> h.setBasicAuth("ACtest", "token123"));
    private final MockRestServiceServer mock = MockRestServiceServer.bindTo(builder).build();
    private final TwilioSmsSender sender = new TwilioSmsSender(builder.build(), "+15005550006");

    private static LinkedMultiValueMap<String, String> form(String to, String body) {
        var m = new LinkedMultiValueMap<String, String>();
        m.add("From", "+15005550006");
        m.add("To", to);
        m.add("Body", body);
        return m;
    }

    @Test
    void domesticNumberBecomesE164AndIsSentWithBasicAuth() {
        String basic = "Basic " + Base64.getEncoder().encodeToString("ACtest:token123".getBytes());
        mock.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", basic))
                .andExpect(content().formData(form("+84901234567", "Ma la 123456")))
                .andRespond(withStatus(HttpStatus.CREATED));

        sender.send("0901 234 567", "Ma la 123456");

        mock.verify();
    }

    @Test
    void internationalNumberKeepsItsPlus() {
        mock.expect(requestTo(URL)).andExpect(content().formData(form("+84901234567", "x")))
                .andRespond(withStatus(HttpStatus.CREATED));

        sender.send("+84 901-234-567", "x");

        mock.verify();
    }

    @Test
    void invalidNumbersAreRejectedWithoutCallingTwilio() {
        for (String bad : new String[] {"123", "abc", "", "0912", "+0123456789"}) {
            BusinessException e = assertThrows(BusinessException.class, () -> sender.send(bad, "x"), bad);
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        }
        mock.verify(); // khong co yeu cau nao duoc ky vong => khong duoc goi
    }

    @Test
    void twilioErrorsBecomeAServiceUnavailableForTheCaller() {
        mock.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        mock.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> sender.send("0901234567", "x")).getStatus());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> sender.send("0901234567", "x")).getStatus());
    }
}
