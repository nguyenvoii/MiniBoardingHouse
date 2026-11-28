package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.group7.minihouse.shared.exception.BusinessException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Client eOTP theo hop dong https://eotp.com.vn/docs/ (khong goi mang that). */
class EotpClientTest {

    private static final String SEND = "https://eotp.com.vn/api/v1/otp/send";
    private static final String VERIFY = "https://eotp.com.vn/api/v1/otp/verify";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://eotp.com.vn/api/v1")
            .defaultHeader("X-Api-Key", "eo_live_test").defaultHeader("X-Api-Secret", "eo_sec_test");
    private final MockRestServiceServer mock = MockRestServiceServer.bindTo(builder).build();
    private final EotpClient client = new EotpClient(builder.build(), "zalo");

    private static MediaType json() {
        return MediaType.APPLICATION_JSON;
    }

    @Test
    void sendDomesticPhoneAddsCountryCodeAndChannelAndKeys() {
        mock.expect(requestTo(SEND)).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "eo_live_test")).andExpect(header("X-Api-Secret", "eo_sec_test"))
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.country_code").value("84"))
                .andExpect(jsonPath("$.channel").value("zalo"))
                .andRespond(withSuccess("{\"ok\":true,\"request_id\":\"abc123\",\"status\":\"queued\",\"expires_in\":300}", json()));

        EotpClient.Sent sent = client.send("0912345678");

        assertEquals("abc123", sent.requestId());
        assertEquals(300, sent.expiresInSeconds());
        mock.verify();
    }

    @Test
    void sendE164PhoneHasNoCountryCode() {
        mock.expect(requestTo(SEND)).andExpect(jsonPath("$.phone").value("+84912345678"))
                .andExpect(jsonPath("$.country_code").doesNotExist())
                .andRespond(withSuccess("{\"ok\":true,\"request_id\":\"r2\"}", json()));

        assertEquals(300, client.send("+84912345678").expiresInSeconds()); // khong co expires_in thi mac dinh 300 giay
        mock.verify();
    }

    @Test
    void invalidPhoneIsABadRequestForTheCaller() {
        mock.expect(requestTo(SEND)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"ok\":false,\"error\":\"invalid_phone\",\"reason\":\"too_short\"}").contentType(json()));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class, () -> client.send("0912")).getStatus());
    }

    @Test
    void quotaOrBalanceOrAuthErrorsAreBadGatewayWithoutLeakingDetails() {
        mock.expect(requestTo(SEND)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"ok\":false,\"error\":\"daily_quota_exceeded\"}").contentType(json()));

        BusinessException e = assertThrows(BusinessException.class, () -> client.send("0912345678"));
        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
    }

    @Test
    void networkFailureIsBadGateway() {
        mock.expect(requestTo(SEND)).andRespond(withException(new IOException("timeout")));

        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(BusinessException.class, () -> client.send("0912345678")).getStatus());
    }

    @Test
    void verifyMapsEveryDocumentedOutcome() {
        String[][] cases = {
                {"200", "{\"ok\":true,\"verified\":true}", "OK"},
                {"400", "{\"ok\":false,\"error\":\"wrong_code\",\"attempts_left\":4}", "WRONG_CODE"},
                {"410", "{\"ok\":false,\"error\":\"expired\"}", "EXPIRED"},
                {"429", "{\"ok\":false,\"error\":\"too_many_attempts\"}", "TOO_MANY"},
                {"404", "{\"ok\":false,\"error\":\"not_found\"}", "NOT_FOUND"},
        };
        for (String[] c : cases) { // dat het ky vong truoc (thu tu), roi moi goi
            mock.expect(requestTo(VERIFY)).andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.request_id").value("abc123")).andExpect(jsonPath("$.code").value("123456"))
                    .andRespond(withStatus(HttpStatus.valueOf(Integer.parseInt(c[0]))).body(c[1]).contentType(json()));
        }
        for (String[] c : cases) {
            assertEquals(EotpClient.Verdict.valueOf(c[2]), client.verify("abc123", " 123456 "), c[2]);
        }
        mock.verify();
    }

    @Test
    void verifyServerErrorIsBadGatewayNotAWrongCode() {
        mock.expect(requestTo(VERIFY)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"ok\":false,\"error\":\"rate_limit_unavailable\"}").contentType(json()));

        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(BusinessException.class, () -> client.verify("abc123", "123456")).getStatus());
    }

    @Test
    void blankCodeIsWrongWithoutCallingTheApi() {
        assertEquals(EotpClient.Verdict.WRONG_CODE, client.verify("abc123", "  "));
        assertEquals(EotpClient.Verdict.WRONG_CODE, client.verify("abc123", null));
        mock.verify(); // khong co yeu cau nao duoc ky vong => khong duoc goi
    }
}
