package com.group7.minihouse.phi.dto;

/** Ngan hang, so tai khoan (6-30 chu so), ten chu tai khoan. */
public record BankAccountRequest(String bankName, String accountNumber, String accountHolder) {
}
