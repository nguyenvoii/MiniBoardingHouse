package com.group7.minihouse.shared.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.group7.minihouse.shared.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Loi do nguoi goi API (sai phuong thuc, sai kieu du lieu, thieu tham so) phai la 4xx, khong phai 500 "Loi he thong". */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void assertFailure(HttpStatus expected, ResponseEntity<ApiResponse<Void>> res) {
        assertEquals(expected, res.getStatusCode());
        assertFalse(res.getBody().isSuccess());
    }

    @Test
    void wrongHttpMethodIs405() {
        assertFailure(HttpStatus.METHOD_NOT_ALLOWED,
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH")));
    }

    @Test
    void unsupportedContentTypeIs415() {
        assertFailure(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException("text/plain")));
    }

    @Test
    void missingOrMalformedParameterIs400() {
        assertFailure(HttpStatus.BAD_REQUEST, handler.handleBadParameter(
                new MissingServletRequestParameterException("page", "int")));
        assertFailure(HttpStatus.BAD_REQUEST, handler.handleBadParameter(
                new MethodArgumentTypeMismatchException("abc", Integer.class, "size", null, null)));
    }
}
