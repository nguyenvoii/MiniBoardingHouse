package com.group7.minihouse.viet.dto;

import com.group7.minihouse.shared.entity.OperatingExpense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho chi phi van hanh khu tro (UC117). */
public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    /** expenseCategory: nhom chi phi tu do (Bao tri, Ve sinh, Thue, Dien nuoc chung...). */
    public record ExpenseRequest(String expenseCategory, String description, BigDecimal amount,
                                 LocalDate expenseDate, String receiptUrl) {
    }

    public record ExpenseResponse(Integer expenseId, String expenseCategory, String description, BigDecimal amount,
                                  LocalDate expenseDate, String receiptUrl, Integer recordedBy,
                                  String recordedByName, LocalDateTime createdAt) {
        public static ExpenseResponse from(OperatingExpense e) {
            return new ExpenseResponse(e.getExpenseId(), e.getExpenseCategory(), e.getDescription(), e.getAmount(),
                    e.getExpenseDate(), e.getReceiptUrl(),
                    e.getRecordedBy() == null ? null : e.getRecordedBy().getUserId(),
                    e.getRecordedBy() == null ? null : e.getRecordedBy().getFullName(), e.getCreatedAt());
        }
    }

    public record CategoryTotal(String expenseCategory, BigDecimal totalAmount, long count) {
    }

    public record ExpenseSummary(LocalDate from, LocalDate to, BigDecimal totalAmount, long count,
                                 List<CategoryTotal> byCategory) {
    }
}
