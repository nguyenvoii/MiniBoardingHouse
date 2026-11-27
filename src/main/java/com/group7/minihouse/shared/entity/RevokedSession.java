package com.group7.minihouse.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "revoked_sessions")
public class RevokedSession {
    @Id
    @Column(name = "session_id", length = 36)
    private String id;

    @Column(name = "expires_at", nullable = false)
    private long expiresAt;
}
