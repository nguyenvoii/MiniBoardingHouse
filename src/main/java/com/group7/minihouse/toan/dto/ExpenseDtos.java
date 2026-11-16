package com.group7.minihouse.toan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO cho chi phi van hanh khu tro. */
public final class ExpenseDtos {

    private ExpenseDtos() {}

    public record ExpenseRequest(Integer propertyId, String expenseCategory, String description,
                                 BigDecimal amount, LocalDate expenseDate, String receiptUrl,
                                 Integer recordedByUserId) {}

    public record ExpenseResponse(Integer expenseId, Integer propertyId, String expenseCategory,
                                  String description, BigDecimal amount, LocalDate expenseDate,
                                  String receiptUrl, Integer recordedByUserId, LocalDateTime createdAt) {}
}
