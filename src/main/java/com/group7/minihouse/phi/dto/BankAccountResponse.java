package com.group7.minihouse.phi.dto;

public record BankAccountResponse(Integer bankAccountId, Integer propertyId, String bankName, String accountNumber,
                                  String accountHolder, String qrImageUrl, boolean isDefault, boolean isActive) {
}
