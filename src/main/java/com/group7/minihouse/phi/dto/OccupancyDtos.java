package com.group7.minihouse.phi.dto;

import java.time.LocalDate;

/** DTO xe cua khach thue va nguoi o cung trong phong: nguon so luong that cho dich vu tinh theo xe / dau nguoi. */
public final class OccupancyDtos {

    private OccupancyDtos() {}

    /** vehicleType: MOTORBIKE, BICYCLE, ELECTRIC_BIKE, CAR, OTHER. */
    public record VehicleRequest(String licensePlate, String vehicleType, String brand, String color) {}

    public record VehicleResponse(Integer vehicleId, Integer tenantId, String licensePlate, String vehicleType,
                                  String brand, String color) {}

    public record ResidentRequest(String fullName, String phoneNumber, String identityNumber, String relationship,
                                  LocalDate startDate, LocalDate dateOfBirth, String permanentAddress,
                                  LocalDate tempResidenceRegisteredOn) {
        public ResidentRequest(String fullName, String phoneNumber, String identityNumber, String relationship,
                               LocalDate startDate) {
            this(fullName, phoneNumber, identityNumber, relationship, startDate, null, null, null);
        }
    }

    /** Nguoi o cung (khong tinh nguoi dung ten hop dong: nguoi do luon duoc tinh la 1 nguoi). */
    public record ResidentResponse(Integer residentId, Integer roomId, String fullName, String phoneNumber,
                                   String identityNumber, String relationship, LocalDate startDate,
                                   LocalDate dateOfBirth, String permanentAddress, LocalDate tempResidenceRegisteredOn) {}
}
