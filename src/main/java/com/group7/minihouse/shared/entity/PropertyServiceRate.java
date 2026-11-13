package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;

/** Bang `property_service_rates`. */
@Entity
@Table(name = "property_service_rates")
public class PropertyServiceRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rate_id")
    private Integer rateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "charge_type", nullable = false, length = 30)
    private String chargeType;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "is_active")
    private Boolean isActive;

    public PropertyServiceRate() {}

    public Integer getRateId() { return rateId; }
    public void setRateId(Integer rateId) { this.rateId = rateId; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getChargeType() { return chargeType; }
    public void setChargeType(String chargeType) { this.chargeType = chargeType; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

}
