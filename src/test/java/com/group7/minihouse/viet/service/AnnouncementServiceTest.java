package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.chi.repository.PropertyFloorRepository;
import com.group7.minihouse.shared.entity.PropertyFloor;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserNotification;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.AnnouncementDtos.AnnouncementDetail;
import com.group7.minihouse.viet.dto.AnnouncementDtos.AnnouncementSendResult;
import com.group7.minihouse.viet.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.group7.minihouse.viet.repository.AnnouncementRecipientRepository;
import com.group7.minihouse.viet.repository.UserNotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** UC130: thông báo chung, không cần DB. */
class AnnouncementServiceTest {

    private final NotificationSender sender = mock(NotificationSender.class);
    private final UserNotificationRepository notifications = mock(UserNotificationRepository.class);
    private final AnnouncementRecipientRepository recipients = mock(AnnouncementRecipientRepository.class);
    private final PropertyFloorRepository floors = mock(PropertyFloorRepository.class);
    private final AnnouncementService service =
            new AnnouncementService(sender, notifications, recipients, floors);

    private static Tenant tenant(int id, String name) {
        Tenant t = mock(Tenant.class);
        when(t.getTenantId()).thenReturn(id);
        when(t.getFullName()).thenReturn(name);
        return t;
    }

    private static Object[] row(Tenant t, String room) {
        return new Object[] {t, room};
    }

    /** Đối số (tạo mock Tenant) được tính xong trước khi gọi hàm, tránh when() lồng trong thenReturn(). */
    private void stubAll(Object[]... rows) {
        when(recipients.findAllRecipients()).thenReturn(List.of(rows));
    }

    private void stubFloors(Object[]... rows) {
        when(recipients.findRecipientsByFloors(anyCollection())).thenReturn(List.of(rows));
    }

    private static CreateAnnouncementRequest all(String title, String content) {
        return new CreateAnnouncementRequest(title, content, "ALL", null);
    }

    @Test
    void tieuDeTrongPhaiTuChoi() {
        assertThrows(BusinessException.class, () -> service.send(all("  ", "Nội dung")));
    }

    @Test
    void noiDungTrongPhaiTuChoi() {
        assertThrows(BusinessException.class, () -> service.send(all("Cắt nước", null)));
    }

    @Test
    void tieuDeQua200KyTuPhaiTuChoi() {
        assertThrows(BusinessException.class, () -> service.send(all("a".repeat(201), "Nội dung")));
    }

    @Test
    void phamViSaiPhaiTuChoi() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest("Cắt nước", "Nội dung", "ROOM", null);
        assertThrows(BusinessException.class, () -> service.send(req));
    }

    @Test
    void guiTheoTangMaThieuDanhSachTangPhaiTuChoi() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest("Cắt nước", "Nội dung", "FLOOR", List.of());
        assertThrows(BusinessException.class, () -> service.send(req));
        verify(recipients, never()).findRecipientsByFloors(anyCollection());
    }

    @Test
    void tangKhongTonTaiPhaiTuChoi() {
        when(floors.findAllById(any())).thenReturn(List.of(mock(PropertyFloor.class)));
        CreateAnnouncementRequest req = new CreateAnnouncementRequest("Cắt nước", "Nội dung", "FLOOR", List.of(1, 99));
        assertThrows(BusinessException.class, () -> service.send(req));
    }

    @Test
    void khongCoNguoiNhanPhaiTuChoiVaKhongGui() {
        stubAll();
        assertThrows(BusinessException.class, () -> service.send(all("Cắt nước", "Nội dung")));
        verify(sender, never()).notifyTenant(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void guiTrungTieuDeTrong60GiayPhaiTra409() {
        stubAll(row(tenant(1, "An"), "P101"));
        when(notifications.existsByReferenceTypeAndTitleAndCreatedAtAfter(eq("ANNOUNCEMENT"), eq("Cắt nước"),
                any(LocalDateTime.class))).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.send(all("Cắt nước", "Nội dung")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(sender, never()).notifyTenant(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void guiToanKhuDemDungSentVaKhachChuaCoTaiKhoan() {
        stubAll(
                row(tenant(1, "An"), "P101"), row(tenant(2, "Bình"), "P102"));
        when(sender.notifyTenant(eq(1), any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(sender.notifyTenant(eq(2), any(), any(), any(), any(), any(), any())).thenReturn(false);
        AnnouncementSendResult r = service.send(all("Cắt nước", "Chào {{ten_khach}}"));
        assertEquals(2, r.recipients());
        assertEquals(1, r.sent());
        assertEquals(1, r.noAccount().size());
        assertEquals("Bình", r.noAccount().get(0).fullName());
    }

    @Test
    void moiNguoiNhanCungMotMaDotVaLoaiANNOUNCEMENT() {
        stubAll(
                row(tenant(1, "An"), "P101"), row(tenant(2, "Bình"), "P102"));
        AnnouncementSendResult r = service.send(all("Cắt nước", "Nội dung"));
        ArgumentCaptor<Integer> batch = ArgumentCaptor.forClass(Integer.class);
        verify(sender, times(2)).notifyTenant(any(), any(), eq("Cắt nước"), eq("Nội dung"), any(),
                eq("ANNOUNCEMENT"), batch.capture());
        assertEquals(r.batchId(), batch.getAllValues().get(0));
        assertEquals(batch.getAllValues().get(0), batch.getAllValues().get(1));
    }

    @Test
    void khachThueNhieuPhongChiNhanMotThongBaoGhiDuMaPhong() {
        Tenant an = tenant(1, "An");
        stubAll(row(an, "P101"), row(an, "P201"));
        AnnouncementSendResult r = service.send(all("Cắt nước", "Nội dung"));
        assertEquals(1, r.recipients());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(sender, times(1)).notifyTenant(eq(1), any(), any(), any(), params.capture(), any(), any());
        assertEquals("P101, P201", params.getValue().get("phong"));
        assertEquals("An", params.getValue().get("ten_khach"));
    }

    @Test
    void guiTheoTangChiTimNguoiOCacTangDaChon() {
        when(floors.findAllById(any())).thenReturn(List.of(mock(PropertyFloor.class), mock(PropertyFloor.class)));
        stubFloors(row(tenant(1, "An"), "P201"));
        CreateAnnouncementRequest req = new CreateAnnouncementRequest("Cắt nước", "Nội dung", "FLOOR", List.of(2, 3));
        assertEquals(1, service.send(req).recipients());
        verify(recipients, never()).findAllRecipients();
    }

    @Test
    void maDotGuiTrungThiSinhLaiMa() {
        stubAll(row(tenant(1, "An"), "P101"));
        when(notifications.existsByReferenceTypeAndReferenceId(eq("ANNOUNCEMENT"), anyInt()))
                .thenReturn(true, false);
        service.send(all("Cắt nước", "Nội dung"));
        verify(notifications, times(2)).existsByReferenceTypeAndReferenceId(eq("ANNOUNCEMENT"), anyInt());
    }

    @Test
    void khongSinhDuocMaDotGuiPhaiTra409() {
        stubAll(row(tenant(1, "An"), "P101"));
        when(notifications.existsByReferenceTypeAndReferenceId(eq("ANNOUNCEMENT"), anyInt())).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.send(all("Cắt nước", "Nội dung")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void chiTietDotKhongTonTaiPhaiTra404() {
        when(notifications.announcementRecipients(eq(5), any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(5));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void chiTietDotDemNguoiDaDocVaDanhSachNguoiNhan() {
        UserNotification n1 = notification(10, "An", false);
        UserNotification n2 = notification(11, "Bình", true);
        when(notifications.announcementRecipients(eq(5), any())).thenReturn(List.of(n1, n2));
        when(notifications.countAnnouncement(5)).thenReturn(2L);
        when(notifications.countAnnouncementRead(5)).thenReturn(1L);
        AnnouncementDetail d = service.detail(5);
        assertEquals(2, d.total());
        assertEquals(1, d.read());
        assertEquals("Cắt nước", d.title());
        assertTrue(!d.recipients().get(0).read() && d.recipients().get(1).read());
    }

    private static UserNotification notification(int userId, String name, boolean read) {
        User u = mock(User.class);
        when(u.getUserId()).thenReturn(userId);
        when(u.getFullName()).thenReturn(name);
        UserNotification n = new UserNotification();
        n.setUser(u);
        n.setTitle("Cắt nước");
        n.setContent("Nội dung");
        n.setIsRead(read);
        n.setCreatedAt(LocalDateTime.of(2026, 11, 18, 9, 0));
        return n;
    }
}
