package com.group7.minihouse.viet.dto;

import java.time.LocalDate;

/** UC52: hop dong sap het han. */
public final class ContractDtos {

    private ContractDtos() {
    }

    public record ExpiringContract(Integer contractId, String contractCode, Integer roomId, String roomCode,
                                   Integer tenantId, String tenantName, LocalDate endDate, long daysLeft,
                                   String status) {
    }

    public record StatusRefreshResult(int markedExpiringSoon, int markedExpired) {
    }
}
