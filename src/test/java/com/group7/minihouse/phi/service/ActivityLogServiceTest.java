package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.ActivityLogResponse;
import com.group7.minihouse.phi.repository.UserActivityLogRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserActivityLog;
import com.group7.minihouse.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

    private UserActivityLog row(int logId, int userId, String name, String action) {
        User u = new User();
        u.setUserId(userId);
        u.setFullName(name);
        UserActivityLog l = new UserActivityLog();
        l.setLogId(logId);
        l.setUser(u);
        l.setActionType(action);
        l.setDescription("mo ta " + logId);
        l.setIpAddress("1.2.3.4");
        l.setCreatedAt(LocalDateTime.of(2026, 11, 14, 8, 0));
        return l;
    }

    @Test
    void searchDefaultsToAllAndCapsPageSize() {
        when(logRepository.search(anyInt(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.search(null, null, null, null, -1, 1000);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(logRepository).search(eq(0), eq(""), from.capture(), to.capture(), pageable.capture());
        assertEquals(LocalDateTime.of(1900, 1, 1, 0, 0), from.getValue());
        assertEquals(LocalDateTime.of(9999, 12, 31, 23, 59, 59), to.getValue());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(0, pageable.getValue().getPageNumber());
    }

    @Test
    void searchMapsRowsWithUserName() {
        when(logRepository.search(eq(7), eq("LOCK_USER"), any(LocalDateTime.class), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row(1, 7, "Nhan Vien A", "LOCK_USER")), PageRequest.of(0, 20), 1));

        PageResponse<ActivityLogResponse> page = service.search(7, " LOCK_USER ", null, null, 0, 20);

        assertEquals(1, page.getTotalElements());
        ActivityLogResponse r = page.getItems().get(0);
        assertEquals(1, r.logId());
        assertEquals(7, r.userId());
        assertEquals("Nhan Vien A", r.userFullName());
        assertEquals("LOCK_USER", r.actionType());
        assertEquals("1.2.3.4", r.ipAddress());
    }

    @Test
    void searchRejectsFromAfterTo() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.search(null, null,
                LocalDateTime.of(2026, 2, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0), 0, 20));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    /** Review Focus: nhat ky cua chinh minh luon loc theo nguoi dung hien tai, khong lo nhat ky nguoi khac. */
    @Test
    void mineAlwaysFiltersByTheCurrentUser() {
        when(logRepository.search(eq(5), eq(""), any(LocalDateTime.class), any(LocalDateTime.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.mine(5, 0, 20);

        verify(logRepository).search(eq(5), eq(""), any(LocalDateTime.class), any(LocalDateTime.class),
                any(Pageable.class));
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
