package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Ham dung chung cho cac service cua Viet. */
final class VietSupport {

    static final int MAX_SIZE = 100;

    private VietSupport() {
    }

    static Pageable page(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE), sort);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** null/blank -> null (khong loc); sai gia tri -> 400. */
    static String enumOrNull(String value, Set<String> allowed, String fieldName) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        v = v.toUpperCase();
        if (!allowed.contains(v)) {
            throw new BusinessException(fieldName + " không hợp lệ, chỉ nhận: " + String.join(", ", allowed));
        }
        return v;
    }

    static String requireText(String value, String fieldName) {
        String v = blankToNull(value);
        if (v == null) {
            throw new BusinessException("Thiếu " + fieldName);
        }
        return v;
    }
}
