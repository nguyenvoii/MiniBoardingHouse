package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserStatusCheckerImplTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserStatusCheckerImpl checker;

    @Test
    void activeUserGetsCurrentRolesFromDatabase() {
        when(userRepository.findSessionState(1, "session")).thenReturn(List.of(
                new Object[]{"ACTIVE", "MANAGER", false, "test-hash"}, new Object[]{"ACTIVE", "CASHIER", false, "test-hash"}));

        assertEquals(Optional.of(List.of("MANAGER", "CASHIER")), checker.state(1, "session").map(com.group7.minihouse.shared.security.UserStatusChecker.State::roles));
    }

    @Test
    void activeUserWithoutRolesGetsEmptyList() {
        when(userRepository.findSessionState(2, "session")).thenReturn(List.<Object[]>of(new Object[]{"ACTIVE", null, false, "test-hash"}));

        Optional<List<String>> roles = checker.state(2, "session").map(com.group7.minihouse.shared.security.UserStatusChecker.State::roles);

        assertTrue(roles.isPresent());
        assertTrue(roles.get().isEmpty());
    }

    @Test
    void lockedOrInactiveUsersHaveNoAccess() {
        when(userRepository.findSessionState(3, "session")).thenReturn(List.<Object[]>of(new Object[]{"LOCKED", "MANAGER"}));
        when(userRepository.findSessionState(4, "session")).thenReturn(List.<Object[]>of(new Object[]{"INACTIVE", "TENANT"}));

        assertEquals(Optional.empty(), checker.state(3, "session").map(com.group7.minihouse.shared.security.UserStatusChecker.State::roles));
        assertEquals(Optional.empty(), checker.state(4, "session").map(com.group7.minihouse.shared.security.UserStatusChecker.State::roles));
    }

    @Test
    void deletedOrUnknownUserHasNoAccess() {
        when(userRepository.findSessionState(99, "session")).thenReturn(List.of());

        assertEquals(Optional.empty(), checker.state(99, "session").map(com.group7.minihouse.shared.security.UserStatusChecker.State::roles));
    }
}
