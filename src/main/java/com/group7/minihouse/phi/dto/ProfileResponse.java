package com.group7.minihouse.phi.dto;

import java.util.List;

public record ProfileResponse(Integer userId, String fullName, String email, String phoneNumber, List<String> roles) {
}
