package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.security.UserStatusChecker;
import org.springframework.stereotype.Component;

/** 1 truy van theo khoa chinh (chi lay cot status) cho moi request co token. */
@Component
public class UserStatusCheckerImpl implements UserStatusChecker {

    private final UserRepository userRepository;

    public UserStatusCheckerImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isActive(Integer userId) {
        return userRepository.findStatusById(userId).map("ACTIVE"::equals).orElse(false);
    }
}
