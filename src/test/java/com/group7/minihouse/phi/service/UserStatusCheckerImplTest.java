package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.repository.UserRepository;
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
    void onlyActiveUsersAreActive() {
        when(userRepository.findStatusById(1)).thenReturn(Optional.of("ACTIVE"));
        when(userRepository.findStatusById(2)).thenReturn(Optional.of("LOCKED"));
        when(userRepository.findStatusById(3)).thenReturn(Optional.of("INACTIVE"));

        assertTrue(checker.isActive(1));
        assertFalse(checker.isActive(2));
        assertFalse(checker.isActive(3));
    }

    @Test
    void deletedOrUnknownUserIsNotActive() {
        when(userRepository.findStatusById(99)).thenReturn(Optional.empty());

        assertFalse(checker.isActive(99));
    }
}
