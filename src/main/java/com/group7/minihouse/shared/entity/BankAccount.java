package com.group7.minihouse.shared.entity;

import jakarta.persistence.*;

/** Bang `bank_accounts`. */
@Entity
@Table(name = "bank_accounts")
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bank_account_id")
    private Integer bankAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    @Column(name = "account_holder", nullable = false, length = 100)
    private String accountHolder;

    @Column(name = "qr_image_url", length = 500)
    private String qrImageUrl;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public BankAccount() {}

    public Integer getBankAccountId() { return bankAccountId; }
    public void setBankAccountId(Integer bankAccountId) { this.bankAccountId = bankAccountId; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getAccountHolder() { return accountHolder; }
    public void setAccountHolder(String accountHolder) { this.accountHolder = accountHolder; }

    public String getQrImageUrl() { return qrImageUrl; }
    public void setQrImageUrl(String qrImageUrl) { this.qrImageUrl = qrImageUrl; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

}
