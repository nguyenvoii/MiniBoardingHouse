package com.group7.minihouse.viet.dto;

import java.math.BigDecimal;
import java.util.List;

public final class TenantRoomDtos {

    private TenantRoomDtos() {}

    public record Resident(String fullName, String relationshipWithPrimary) {}

    public record TenantRoom(Integer roomId, String roomCode, BigDecimal areaSqm, Integer maxOccupants,
                             Integer contractId, String contractCode, List<Resident> residents) {}
}
