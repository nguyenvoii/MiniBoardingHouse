package com.group7.minihouse.chi.dto;
import java.math.BigDecimal;
public record RoomTypeDTO(Integer roomTypeId, Integer propertyId, String typeName, String description, BigDecimal defaultRentPrice, BigDecimal defaultDepositAmount) {}
