package com.group7.minihouse.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `rental_contracts`. */
@Entity
@Table(name = "rental_contracts")
public class RentalContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id")
    private Integer contractId;

    @Column(name = "contract_code", nullable = false, unique = true, length = 50)
    private String contractCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "template_id")
    private ContractTemplate template;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "monthly_rent_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyRentPrice;

    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "payment_cycle_months", nullable = false)
    private Integer paymentCycleMonths;

    @Column(name = "payment_due_day", nullable = false)
    private Integer paymentDueDay;

    @Column(name = "late_fee_per_day", precision = 18, scale = 2)
    private BigDecimal lateFeePerDay;

    @Column(name = "electric_terms", length = 255)
    private String electricTerms;

    @Column(name = "water_terms", length = 255)
    private String waterTerms;

    @Column(name = "deposit_refund_conditions", columnDefinition = "NVARCHAR(MAX)")
    private String depositRefundConditions;

    @Column(name = "digital_signature_url", length = 500)
    private String digitalSignatureUrl;

    @Column(name = "pdf_contract_url", length = 500)
    private String pdfContractUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public RentalContract() {}

    public Integer getContractId() { return contractId; }
    public void setContractId(Integer contractId) { this.contractId = contractId; }

    public String getContractCode() { return contractCode; }
    public void setContractCode(String contractCode) { this.contractCode = contractCode; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public ContractTemplate getTemplate() { return template; }
    public void setTemplate(ContractTemplate template) { this.template = template; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getMonthlyRentPrice() { return monthlyRentPrice; }
    public void setMonthlyRentPrice(BigDecimal monthlyRentPrice) { this.monthlyRentPrice = monthlyRentPrice; }

    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }

    public Integer getPaymentCycleMonths() { return paymentCycleMonths; }
    public void setPaymentCycleMonths(Integer paymentCycleMonths) { this.paymentCycleMonths = paymentCycleMonths; }

    public Integer getPaymentDueDay() { return paymentDueDay; }
    public void setPaymentDueDay(Integer paymentDueDay) { this.paymentDueDay = paymentDueDay; }

    public BigDecimal getLateFeePerDay() { return lateFeePerDay; }
    public void setLateFeePerDay(BigDecimal lateFeePerDay) { this.lateFeePerDay = lateFeePerDay; }

    public String getElectricTerms() { return electricTerms; }
    public void setElectricTerms(String electricTerms) { this.electricTerms = electricTerms; }

    public String getWaterTerms() { return waterTerms; }
    public void setWaterTerms(String waterTerms) { this.waterTerms = waterTerms; }

    public String getDepositRefundConditions() { return depositRefundConditions; }
    public void setDepositRefundConditions(String depositRefundConditions) { this.depositRefundConditions = depositRefundConditions; }

    public String getDigitalSignatureUrl() { return digitalSignatureUrl; }
    public void setDigitalSignatureUrl(String digitalSignatureUrl) { this.digitalSignatureUrl = digitalSignatureUrl; }

    public String getPdfContractUrl() { return pdfContractUrl; }
    public void setPdfContractUrl(String pdfContractUrl) { this.pdfContractUrl = pdfContractUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
