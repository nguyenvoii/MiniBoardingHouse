package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `calculated_monthly_fees`. */
@DynamicInsert
@Entity
@Table(name = "calculated_monthly_fees")
public class CalculatedMonthlyFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calculated_fee_id")
    private Integer calculatedFeeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "billing_period_id", nullable = false)
    private MonthlyBillingPeriod billingPeriod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private RentalContract contract;

    @Column(name = "rent_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal rentAmount;

    @Column(name = "electric_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal electricAmount;

    @Column(name = "water_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal waterAmount;

    @Column(name = "service_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal serviceAmount;

    @Column(name = "surcharge_amount", precision = 18, scale = 2)
    private BigDecimal surchargeAmount;

    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "late_penalty_amount", precision = 18, scale = 2)
    private BigDecimal latePenaltyAmount;

    @Column(name = "total_calculated_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalCalculatedAmount;

    public CalculatedMonthlyFee() {}

    public Integer getCalculatedFeeId() { return calculatedFeeId; }
    public void setCalculatedFeeId(Integer calculatedFeeId) { this.calculatedFeeId = calculatedFeeId; }

    public MonthlyBillingPeriod getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(MonthlyBillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public RentalContract getContract() { return contract; }
    public void setContract(RentalContract contract) { this.contract = contract; }

    public BigDecimal getRentAmount() { return rentAmount; }
    public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }

    public BigDecimal getElectricAmount() { return electricAmount; }
    public void setElectricAmount(BigDecimal electricAmount) { this.electricAmount = electricAmount; }

    public BigDecimal getWaterAmount() { return waterAmount; }
    public void setWaterAmount(BigDecimal waterAmount) { this.waterAmount = waterAmount; }

    public BigDecimal getServiceAmount() { return serviceAmount; }
    public void setServiceAmount(BigDecimal serviceAmount) { this.serviceAmount = serviceAmount; }

    public BigDecimal getSurchargeAmount() { return surchargeAmount; }
    public void setSurchargeAmount(BigDecimal surchargeAmount) { this.surchargeAmount = surchargeAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getLatePenaltyAmount() { return latePenaltyAmount; }
    public void setLatePenaltyAmount(BigDecimal latePenaltyAmount) { this.latePenaltyAmount = latePenaltyAmount; }

    public BigDecimal getTotalCalculatedAmount() { return totalCalculatedAmount; }
    public void setTotalCalculatedAmount(BigDecimal totalCalculatedAmount) { this.totalCalculatedAmount = totalCalculatedAmount; }

}
