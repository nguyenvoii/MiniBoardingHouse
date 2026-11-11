package com.group7.minihouse.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `booking_requests`. */
@Entity
@Table(name = "booking_requests")
public class BookingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Integer bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "expected_start_date", nullable = false)
    private LocalDate expectedStartDate;

    @Column(name = "occupants_count")
    private Integer occupantsCount;

    @Column(name = "deposit_paid", precision = 18, scale = 2)
    private BigDecimal depositPaid;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public BookingRequest() {}

    public Integer getBookingId() { return bookingId; }
    public void setBookingId(Integer bookingId) { this.bookingId = bookingId; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public LocalDate getExpectedStartDate() { return expectedStartDate; }
    public void setExpectedStartDate(LocalDate expectedStartDate) { this.expectedStartDate = expectedStartDate; }

    public Integer getOccupantsCount() { return occupantsCount; }
    public void setOccupantsCount(Integer occupantsCount) { this.occupantsCount = occupantsCount; }

    public BigDecimal getDepositPaid() { return depositPaid; }
    public void setDepositPaid(BigDecimal depositPaid) { this.depositPaid = depositPaid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
