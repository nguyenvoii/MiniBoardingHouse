package com.group7.minihouse.viet.dto;

import com.group7.minihouse.shared.entity.DebtPenaltyLog;
import com.group7.minihouse.shared.entity.DebtProcessingLog;
import com.group7.minihouse.shared.entity.DebtReminder;
import com.group7.minihouse.shared.entity.TenantDebt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho phan cong no (UC93-100). */
public final class DebtDtos {

    private DebtDtos() {
    }

    public record DebtResponse(Integer debtId, Integer tenantId, String tenantName, Integer roomId, String roomCode,
                               Integer invoiceId, BigDecimal openingDebtBalance, BigDecimal currentPeriodDebt,
                               BigDecimal totalOutstandingDebt, Integer overdueDays, String debtStatus,
                               LocalDateTime updatedAt) {
        public static DebtResponse from(TenantDebt d) {
            return new DebtResponse(d.getDebtId(), d.getTenant().getTenantId(), d.getTenant().getFullName(),
                    d.getRoom().getRoomId(), d.getRoom().getRoomCode(),
                    d.getInvoice() == null ? null : d.getInvoice().getInvoiceId(),
                    d.getOpeningDebtBalance(), d.getCurrentPeriodDebt(), d.getTotalOutstandingDebt(),
                    d.getOverdueDays(), d.getDebtStatus(), d.getUpdatedAt());
        }
    }

    public record PenaltyResponse(Integer penaltyId, Integer overdueDays, BigDecimal dailyPenaltyRate,
                                  BigDecimal calculatedPenaltyAmount, LocalDateTime createdAt) {
        public static PenaltyResponse from(DebtPenaltyLog p) {
            return new PenaltyResponse(p.getPenaltyId(), p.getOverdueDays(), p.getDailyPenaltyRate(),
                    p.getCalculatedPenaltyAmount(), p.getCreatedAt());
        }
    }

    public record ReminderResponse(Integer reminderId, String reminderLevel, String messageContent,
                                   LocalDateTime sentAt) {
        public static ReminderResponse from(DebtReminder r) {
            return new ReminderResponse(r.getReminderId(), r.getReminderLevel(), r.getMessageContent(), r.getSentAt());
        }
    }

    public record ProcessingLogResponse(Integer procLogId, LocalDate promisedPaymentDate, String delayReason,
                                        String staffNote, Integer recordedBy, String recordedByName,
                                        LocalDateTime recordedAt) {
        public static ProcessingLogResponse from(DebtProcessingLog l) {
            return new ProcessingLogResponse(l.getProcLogId(), l.getPromisedPaymentDate(), l.getDelayReason(),
                    l.getStaffNote(), l.getRecordedBy().getUserId(), l.getRecordedBy().getFullName(),
                    l.getRecordedAt());
        }
    }

    public record DebtDetailResponse(DebtResponse debt, BigDecimal latestPenaltyAmount,
                                     List<PenaltyResponse> penalties, List<ReminderResponse> reminders,
                                     List<ProcessingLogResponse> processingLogs) {
    }

    /** UC100: ghi nhan cam ket thanh toan / ly do cham / ghi chu xu ly. */
    public record ProcessingLogRequest(LocalDate promisedPaymentDate, String delayReason, String staffNote) {
    }

    public record InvoiceHistoryItem(Integer invoiceId, String invoiceCode, LocalDate issueDate, LocalDate dueDate,
                                     BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal remainingAmount,
                                     String paymentStatus) {
    }

    public record StatusSummary(String status, long count, BigDecimal totalAmount) {
    }

    /** UC97, UC98: tong cong no toan khu. */
    public record DebtSummaryResponse(BigDecimal totalOutstanding, long debtorCount, List<StatusSummary> byStatus) {
    }

    public record RecalcResult(int processed, int cleared, int penaltiesLogged) {
    }

    public record ReminderRunResult(int sent) {
    }
}
