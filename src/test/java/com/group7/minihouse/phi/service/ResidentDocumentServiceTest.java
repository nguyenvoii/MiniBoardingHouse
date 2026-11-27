package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomResident;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.storage.FileStorage;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Anh CCCD nguoi o cung va nhac han khai bao tam tru (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResidentDocumentServiceTest {

    @Mock EntityManager em;
    @Mock FileStorage storage;
    @Mock ActivityLogService activityLogService;

    ResidentDocumentService service;
    RoomResident resident;

    @BeforeEach
    void setUp() {
        service = new ResidentDocumentService(em, storage, activityLogService, Clock.systemDefaultZone());
        Room room = new Room();
        room.setRoomId(1);
        room.setRoomCode("P101");
        resident = new RoomResident();
        resident.setResidentId(5);
        resident.setRoom(room);
        resident.setFullName("Nguyen Thi Hoa");
        resident.setStartDate(LocalDate.of(2026, 11, 1));
        when(em.find(RoomResident.class, 5)).thenReturn(resident);
    }

    @Test
    void uploadNeedsAtLeastOneImage() {
        assertThrows(BusinessException.class, () -> service.uploadIdCard(5, null, new byte[0], 1, "ip"));
        verify(storage, never()).saveImage(any(), any(), anyLong());
    }

    @Test
    void replacingFrontKeepsBackAndDeletesOldFrontFile() {
        resident.setIdFrontImageUrl("/api/residents/5/id-card/photos/old-front.png");
        resident.setIdBackImageUrl("/api/residents/5/id-card/photos/back.png");
        when(storage.saveImage(any(), any(), anyLong())).thenReturn("new-front.png");

        service.uploadIdCard(5, new byte[] {1}, null, 1, "ip");

        assertEquals("/api/residents/5/id-card/photos/new-front.png", resident.getIdFrontImageUrl());
        assertEquals("/api/residents/5/id-card/photos/back.png", resident.getIdBackImageUrl());
        verify(storage).delete("resident-id-cards", "old-front.png");
        verify(storage, never()).delete("resident-id-cards", "back.png");
    }

    @Test
    void photoOfAnotherUrlIsNotServed() {
        resident.setIdFrontImageUrl("/api/residents/5/id-card/photos/a.png");
        when(storage.find("resident-id-cards", "a.png")).thenReturn(Optional.of(java.nio.file.Path.of("a.png")));
        assertTrue(service.photo(5, "a.png").isPresent());
        assertFalse(service.photo(5, "b.png").isPresent());
    }

    @Test
    void dueDateIs30DaysAfterMovingInAndOverdueAfterThat() {
        var onDue = ResidentDocumentService.toDue(resident, LocalDate.of(2026, 12, 1)); // dung ngay han
        assertEquals(LocalDate.of(2026, 12, 1), onDue.dueDate());
        assertFalse(onDue.overdue());
        assertTrue(ResidentDocumentService.toDue(resident, LocalDate.of(2026, 12, 2)).overdue());
    }
}
