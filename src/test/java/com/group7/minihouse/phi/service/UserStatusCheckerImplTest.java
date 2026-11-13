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
        when(userRepository.findStatusAndRoleCodes(1)).thenReturn(List.of(
                new Object[]{"ACTIVE", "MANAGER"}, new Object[]{"ACTIVE", "CASHIER"}));

        assertEquals(Optional.of(List.of("MANAGER", "CASHIER")), checker.activeRoles(1));
    }

    @Test
    void activeUserWithoutRolesGetsEmptyList() {
        when(userRepository.findStatusAndRoleCodes(2)).thenReturn(List.<Object[]>of(new Object[]{"ACTIVE", null}));

        Optional<List<String>> roles = checker.activeRoles(2);

        assertTrue(roles.isPresent());
        assertTrue(roles.get().isEmpty());
    }

    @Test
    void lockedOrInactiveUsersHaveNoAccess() {
        when(userRepository.findStatusAndRoleCodes(3)).thenReturn(List.<Object[]>of(new Object[]{"LOCKED", "MANAGER"}));
        when(userRepository.findStatusAndRoleCodes(4)).thenReturn(List.<Object[]>of(new Object[]{"INACTIVE", "TENANT"}));

        assertEquals(Optional.empty(), checker.activeRoles(3));
        assertEquals(Optional.empty(), checker.activeRoles(4));
    }

    @Test
    void deletedOrUnknownUserHasNoAccess() {
        when(userRepository.findStatusAndRoleCodes(99)).thenReturn(List.of());

        assertEquals(Optional.empty(), checker.activeRoles(99));
    }
}
