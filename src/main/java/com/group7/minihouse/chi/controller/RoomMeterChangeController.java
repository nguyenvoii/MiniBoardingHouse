package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chi/rooms/{roomId}/meter-changes")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class RoomMeterChangeController {
    public record Change(Integer changeId, String meterKind, LocalDate installedOn, String serialNumber,
                         Integer previousReading, Integer installedReading) {}

    private final JdbcTemplate jdbc;
    private final RoomRepository rooms;

    public RoomMeterChangeController(JdbcTemplate jdbc, RoomRepository rooms) {
        this.jdbc = jdbc;
        this.rooms = rooms;
    }

    @GetMapping
    public ApiResponse<List<Change>> list(@PathVariable int roomId) {
        if (!rooms.existsById(roomId)) throw BusinessException.notFound("Phòng");
        return ApiResponse.ok(jdbc.query("""
                SELECT change_id, meter_kind, installed_on, serial_number, previous_reading, installed_reading
                FROM dbo.room_meter_changes WHERE room_id = ?
                ORDER BY installed_on DESC, change_id DESC
                """, (rs, row) -> new Change(rs.getInt("change_id"), rs.getString("meter_kind"),
                rs.getDate("installed_on").toLocalDate(), rs.getString("serial_number"),
                rs.getObject("previous_reading", Integer.class), rs.getInt("installed_reading")), roomId));
    }

    @PostMapping
    public ApiResponse<Void> add(@PathVariable int roomId, @RequestBody Change change) {
        if (!rooms.existsById(roomId)) throw BusinessException.notFound("Phòng");
        String serial = change.serialNumber() == null ? "" : change.serialNumber().trim();
        if ((!"ELECTRIC".equals(change.meterKind()) && !"WATER".equals(change.meterKind()))
                || change.installedOn() == null || change.installedOn().isAfter(LocalDate.now())
                || serial.isEmpty() || serial.length() > 80
                || change.installedReading() == null || change.installedReading() < 0
                || (change.previousReading() != null && change.previousReading() < 0)) {
            throw new BusinessException("Kiểm tra loại công tơ, ngày lắp, số sê-ri và chỉ số");
        }
        try {
            jdbc.update("""
                    INSERT INTO dbo.room_meter_changes
                        (room_id, meter_kind, installed_on, serial_number, previous_reading, installed_reading)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, roomId, change.meterKind(), java.sql.Date.valueOf(change.installedOn()), serial,
                    change.previousReading(), change.installedReading());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "Thiết bị này đã được ghi nhận trong ngày");
        }
        return ApiResponse.ok("Đã lưu lịch sử công tơ", null);
    }
}
