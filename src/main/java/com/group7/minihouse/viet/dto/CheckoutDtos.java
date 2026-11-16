package com.group7.minihouse.viet.dto;

import com.group7.minihouse.shared.entity.CheckoutRequest;
import com.group7.minihouse.shared.entity.DepositRefundVoucher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho tra phong, quyet toan, hoan coc (UC101-107). */
public final class CheckoutDtos {

    private CheckoutDtos() {
    }

    /** contractId co the bo trong neu nguoi thue chi co dung 1 hop dong dang hieu luc. */
    public record CreateCheckoutRequest(Integer contractId, LocalDate expectedCheckoutDate) {
    }

    /** UC101: xac nhan lich kiem tra phong (co the doi ngay tra phong du kien). */
    public record ConfirmScheduleRequest(LocalDate expectedCheckoutDate) {
    }

    public record DamageItem(String name, BigDecimal cost) {
    }

    /** UC102, UC103: ghi nhan hien trang phong; tien boi thuong = tong cost cac thiet bi hu hong. */
    public record InspectionRequest(Integer finalElectricMeter, Integer finalWaterMeter, List<DamageItem> damages,
                                    String inspectionPhotoUrl) {
    }

    public record CheckoutResponse(Integer checkoutId, Integer contractId, String contractCode, Integer roomId,
                                   String roomCode, Integer tenantId, String tenantName,
                                   LocalDate expectedCheckoutDate, LocalDate actualCheckoutDate, String status,
                                   LocalDateTime createdAt) {
        public static CheckoutResponse from(CheckoutRequest c) {
            return new CheckoutResponse(c.getCheckoutId(), c.getContract().getContractId(),
                    c.getContract().getContractCode(), c.getContract().getRoom().getRoomId(),
                    c.getContract().getRoom().getRoomCode(), c.getContract().getTenant().getTenantId(),
                    c.getContract().getTenant().getFullName(), c.getExpectedCheckoutDate(),
                    c.getActualCheckoutDate(), c.getStatus(), c.getCreatedAt());
        }
    }

    public record InspectionResponse(Integer inspectionId, Integer checkoutId, Integer finalElectricMeter,
                                     Integer finalWaterMeter, String damagedEquipmentsDescription,
                                     BigDecimal damageCompensationAmount, BigDecimal unpaidUtilitiesAmount,
                                     String inspectionPhotoUrl, Integer inspectedBy, LocalDateTime inspectedAt) {
    }

    public record UnpaidInvoiceItem(Integer invoiceId, String invoiceCode, LocalDate dueDate,
                                    BigDecimal remainingAmount) {
    }

    /** UC104, UC105: quyet toan - khau tru cong no va boi thuong vao tien coc. */
    public record SettlementResponse(Integer checkoutId, BigDecimal originalDeposit, BigDecimal debtTotal,
                                     BigDecimal damageTotal, BigDecimal deductedDebt, BigDecimal deductedDamage,
                                     BigDecimal refundable, BigDecimal tenantMustPay,
                                     List<UnpaidInvoiceItem> unpaidInvoices) {
    }

    public record RefundVoucherResponse(Integer refundId, Integer checkoutId, BigDecimal originalDepositAmount,
                                        BigDecimal deductedDebtAmount, BigDecimal deductedDamageAmount,
                                        BigDecimal actualRefundAmount, LocalDateTime refundDate, String status) {
        public static RefundVoucherResponse from(DepositRefundVoucher v) {
            return new RefundVoucherResponse(v.getRefundId(), v.getCheckout().getCheckoutId(),
                    v.getOriginalDepositAmount(), v.getDeductedDebtAmount(), v.getDeductedDamageAmount(),
                    v.getActualRefundAmount(), v.getRefundDate(), v.getStatus());
        }
    }

    /** UC107: bien ban ban giao phong (du lieu de in). */
    public record HandoverReport(CheckoutResponse checkout, InspectionResponse inspection,
                                 SettlementResponse settlement, RefundVoucherResponse refundVoucher) {
    }
}
