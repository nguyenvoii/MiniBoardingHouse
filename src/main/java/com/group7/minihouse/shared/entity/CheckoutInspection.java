package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `checkout_inspections`. */
@Entity
@Table(name = "checkout_inspections")
public class CheckoutInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Integer inspectionId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkout_id", nullable = false)
    private CheckoutRequest checkout;

    @Column(name = "final_electric_meter", nullable = false)
    private Integer finalElectricMeter;

    @Column(name = "final_water_meter", nullable = false)
    private Integer finalWaterMeter;

    @Column(name = "damaged_equipments_description", columnDefinition = "NVARCHAR(MAX)")
    private String damagedEquipmentsDescription;

    @Column(name = "damage_compensation_amount", precision = 18, scale = 2)
    private BigDecimal damageCompensationAmount;

    @Column(name = "unpaid_utilities_amount", precision = 18, scale = 2)
    private BigDecimal unpaidUtilitiesAmount;

    @Column(name = "inspection_photo_url", length = 500)
    private String inspectionPhotoUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspected_by", nullable = false)
    private User inspectedBy;

    @Column(name = "inspected_at", nullable = false)
    private LocalDateTime inspectedAt;

    public CheckoutInspection() {}

    public Integer getInspectionId() { return inspectionId; }
    public void setInspectionId(Integer inspectionId) { this.inspectionId = inspectionId; }

    public CheckoutRequest getCheckout() { return checkout; }
    public void setCheckout(CheckoutRequest checkout) { this.checkout = checkout; }

    public Integer getFinalElectricMeter() { return finalElectricMeter; }
    public void setFinalElectricMeter(Integer finalElectricMeter) { this.finalElectricMeter = finalElectricMeter; }

    public Integer getFinalWaterMeter() { return finalWaterMeter; }
    public void setFinalWaterMeter(Integer finalWaterMeter) { this.finalWaterMeter = finalWaterMeter; }

    public String getDamagedEquipmentsDescription() { return damagedEquipmentsDescription; }
    public void setDamagedEquipmentsDescription(String damagedEquipmentsDescription) { this.damagedEquipmentsDescription = damagedEquipmentsDescription; }

    public BigDecimal getDamageCompensationAmount() { return damageCompensationAmount; }
    public void setDamageCompensationAmount(BigDecimal damageCompensationAmount) { this.damageCompensationAmount = damageCompensationAmount; }

    public BigDecimal getUnpaidUtilitiesAmount() { return unpaidUtilitiesAmount; }
    public void setUnpaidUtilitiesAmount(BigDecimal unpaidUtilitiesAmount) { this.unpaidUtilitiesAmount = unpaidUtilitiesAmount; }

    public String getInspectionPhotoUrl() { return inspectionPhotoUrl; }
    public void setInspectionPhotoUrl(String inspectionPhotoUrl) { this.inspectionPhotoUrl = inspectionPhotoUrl; }

    public User getInspectedBy() { return inspectedBy; }
    public void setInspectedBy(User inspectedBy) { this.inspectedBy = inspectedBy; }

    public LocalDateTime getInspectedAt() { return inspectedAt; }
    public void setInspectedAt(LocalDateTime inspectedAt) { this.inspectedAt = inspectedAt; }

}
