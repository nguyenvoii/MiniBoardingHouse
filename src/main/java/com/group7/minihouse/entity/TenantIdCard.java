package com.group7.minihouse.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `tenant_id_cards`. */
@Entity
@Table(name = "tenant_id_cards")
public class TenantIdCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Integer cardId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "front_image_url", nullable = false, length = 500)
    private String frontImageUrl;

    @Column(name = "back_image_url", nullable = false, length = 500)
    private String backImageUrl;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public TenantIdCard() {}

    public Integer getCardId() { return cardId; }
    public void setCardId(Integer cardId) { this.cardId = cardId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getFrontImageUrl() { return frontImageUrl; }
    public void setFrontImageUrl(String frontImageUrl) { this.frontImageUrl = frontImageUrl; }

    public String getBackImageUrl() { return backImageUrl; }
    public void setBackImageUrl(String backImageUrl) { this.backImageUrl = backImageUrl; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

}
