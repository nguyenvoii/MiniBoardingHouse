package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.repository.UserActivityLogRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserActivityLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    @Mock UserActivityLogRepository logRepository;
    @Mock UserRepository userRepository;
    ActivityLogService service;

    @BeforeEach
    void setUp() {
        service = new ActivityLogService(logRepository, userRepository);
    }

    @Test
    void logsActionForUser() {
        User u = new User();
        u.setUserId(5);
        when(userRepository.getReferenceById(5)).thenReturn(u);

        service.log(5, "LOGOUT", "Dang xuat", "1.2.3.4");

        ArgumentCaptor<UserActivityLog> c = ArgumentCaptor.forClass(UserActivityLog.class);
        verify(logRepository).save(c.capture());
        assertSame(u, c.getValue().getUser());
        assertEquals("LOGOUT", c.getValue().getActionType());
        assertEquals("Dang xuat", c.getValue().getDescription());
        assertEquals("1.2.3.4", c.getValue().getIpAddress());
    }
}
