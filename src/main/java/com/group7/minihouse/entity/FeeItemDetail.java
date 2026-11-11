package com.group7.minihouse.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;

/** Bang `fee_item_details`. */
@Entity
@Table(name = "fee_item_details")
public class FeeItemDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Integer detailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calculated_fee_id", nullable = false)
    private CalculatedMonthlyFee calculatedFee;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal;

    public FeeItemDetail() {}

    public Integer getDetailId() { return detailId; }
    public void setDetailId(Integer detailId) { this.detailId = detailId; }

    public CalculatedMonthlyFee getCalculatedFee() { return calculatedFee; }
    public void setCalculatedFee(CalculatedMonthlyFee calculatedFee) { this.calculatedFee = calculatedFee; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

}
