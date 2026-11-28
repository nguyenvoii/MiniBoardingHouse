package com.group7.minihouse.shared.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.group7.minihouse.shared.exception.BusinessException;
import java.io.IOException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Gui SMS qua SpeedSMS (khong goi mang that). */
class SpeedSmsSenderTest {

    private static final String BASE = "https://api.speedsms.vn/index.php";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer mock = MockRestServiceServer.bindTo(builder).build();

    private SpeedSmsSender sender(String type, String from) {
        return new SpeedSmsSender(builder.build(), BASE, "tok en&1", type, from);
    }

    private static final String OK = "{\"status\":\"success\",\"code\":\"00\",\"data\":{\"tranId\":1,\"totalSMS\":1,\"totalPrice\":300}}";

    @Test
    void domesticNumberBecomes84AndEveryValueIsUrlEncoded() {
        mock.expect(requestTo(Matchers.startsWith(BASE + "/sms/send?"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("access-token", "tok+en%261")) // "tok en&1": dau cach va & phai duoc ma hoa de khong tach sai tham so
                .andExpect(queryParam("to", "84901234567"))
                .andExpect(queryParam("content", "Ma+la+123456+%2810+phut%29."))
                .andExpect(queryParam("type", "4"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        sender("4", "").send("0901 234 567", "Ma la 123456 (10 phut).");

        mock.verify();
    }

    @Test
    void plusAndCountryPrefixFormsAreAccepted() {
        mock.expect(requestTo(Matchers.startsWith(BASE))).andExpect(queryParam("to", "84901234567"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        mock.expect(requestTo(Matchers.startsWith(BASE))).andExpect(queryParam("to", "84901234567"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        sender("4", "").send("+84901234567", "x");
        sender("4", "").send("84901234567", "x");

        mock.verify();
    }

    @Test
    void senderAndTypeAreSentWhenConfigured() {
        mock.expect(requestTo(Matchers.startsWith(BASE))).andExpect(queryParam("type", "3"))
                .andExpect(queryParam("sender", "STAYLY")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        sender("3", "STAYLY").send("0901234567", "x");

        mock.verify();
    }

    @Test
    void invalidNumbersAreRejectedWithoutCallingSpeedSms() {
        for (String bad : new String[] {"123", "abc", "", "0912", "+0123456789", "090123456789"}) {
            BusinessException e = assertThrows(BusinessException.class, () -> sender("4", "").send(bad, "x"), bad);
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        }
        mock.verify(); // khong co yeu cau nao duoc ky vong => khong duoc goi
    }

    @Test
    void speedSmsInvalidPhoneCodeIsABadRequest() {
        mock.expect(requestTo(Matchers.startsWith(BASE))).andRespond(withSuccess(
                "{\"status\":\"error\",\"code\":\"105\",\"message\":\"Invalid phone\"}", MediaType.APPLICATION_JSON));

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(BusinessException.class, () -> sender("4", "").send("0901234567", "x")).getStatus());
    }

    @Test
    void balanceBlockedOrServerErrorsBecomeServiceUnavailableWithoutLeakingTheToken() {
        mock.expect(requestTo(Matchers.startsWith(BASE))).andRespond(withSuccess(
                "{\"status\":\"error\",\"code\":\"300\",\"message\":\"Insufficient balance\"}", MediaType.APPLICATION_JSON));
        mock.expect(requestTo(Matchers.startsWith(BASE))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("oops"));
        mock.expect(requestTo(Matchers.startsWith(BASE))).andRespond(withException(new IOException("timeout")));

        for (int i = 0; i < 3; i++) {
            BusinessException e = assertThrows(BusinessException.class, () -> sender("4", "").send("0901234567", "x"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
            assertEquals("Tạm thời không gửi được SMS", e.getMessage());
        }
    }

    @Test
    void enabledWithoutTokenRefusesToStart() {
        assertThrows(IllegalStateException.class, () -> new SpeedSmsSender("  ", "4", "", BASE));
    }
}
