package com.group7.minihouse.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `checkout_requests`. */
@Entity
@Table(name = "checkout_requests")
public class CheckoutRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "checkout_id")
    private Integer checkoutId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private RentalContract contract;

    @Column(name = "expected_checkout_date", nullable = false)
    private LocalDate expectedCheckoutDate;

    @Column(name = "actual_checkout_date")
    private LocalDate actualCheckoutDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public CheckoutRequest() {}

    public Integer getCheckoutId() { return checkoutId; }
    public void setCheckoutId(Integer checkoutId) { this.checkoutId = checkoutId; }

    public RentalContract getContract() { return contract; }
    public void setContract(RentalContract contract) { this.contract = contract; }

    public LocalDate getExpectedCheckoutDate() { return expectedCheckoutDate; }
    public void setExpectedCheckoutDate(LocalDate expectedCheckoutDate) { this.expectedCheckoutDate = expectedCheckoutDate; }

    public LocalDate getActualCheckoutDate() { return actualCheckoutDate; }
    public void setActualCheckoutDate(LocalDate actualCheckoutDate) { this.actualCheckoutDate = actualCheckoutDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
