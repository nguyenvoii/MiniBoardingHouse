package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.group7.minihouse.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayOsClientTest {

    private static final String URL = "https://api-merchant.payos.vn/v2/payment-requests";
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api-merchant.payos.vn");
    private final MockRestServiceServer mock = MockRestServiceServer.bindTo(builder).build();
    private final PayOsClient client = new PayOsClient(builder.build(), "test-key",
            "https://example.test/ok", "https://example.test/cancel");

    @Test
    void createsSignedCheckoutForTheExpectedOrder() {
        mock.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.orderCode").value(123))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.signature").value("f89f97db0ae556c37b3144cc612b981e88908b4f531206e99cd5325f3d12b814"))
                .andRespond(withSuccess("""
                        {"code":"00","data":{"orderCode":123,"amount":1000,
                        "checkoutUrl":"https://pay.payos.vn/web/example"}}
                        """, MediaType.APPLICATION_JSON));
        assertEquals("https://pay.payos.vn/web/example", client.create(123, 1000).checkoutUrl());
        mock.verify();
    }

    @Test
    void confirmsOnlyExactIntegerAmountForPaidOrder() {
        mock.expect(requestTo(URL + "/123")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"code":"00","data":{"orderCode":123,"amount":1000,"amountPaid":1000.01,"status":"PAID"}}
                        """, MediaType.APPLICATION_JSON));
        assertFalse(client.paid(123, 1000));
        mock.verify();
    }

    @Test
    void rejectsMismatchedOrderEvenWhenProviderSaysPaid() {
        mock.expect(requestTo(URL + "/123")).andRespond(withSuccess("""
                {"code":"00","data":{"orderCode":124,"amount":1000,"amountPaid":1000,"status":"PAID"}}
                """, MediaType.APPLICATION_JSON));
        assertThrows(BusinessException.class, () -> client.paid(123, 1000));
        mock.verify();
    }
}
