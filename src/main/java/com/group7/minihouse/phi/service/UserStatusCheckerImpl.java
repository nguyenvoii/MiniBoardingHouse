package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.security.UserStatusChecker;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 1 truy van (status + vai tro) theo khoa chinh cho moi request co token. */
@Component
public class UserStatusCheckerImpl implements UserStatusChecker {

    private final UserRepository userRepository;

    public UserStatusCheckerImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<List<String>> activeRoles(Integer userId) {
        List<Object[]> rows = userRepository.findStatusAndRoleCodes(userId);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0)[0])) {
            return Optional.empty();
        }
        return Optional.of(rows.stream().map(r -> (String) r[1]).filter(Objects::nonNull).toList());
    }
}
