package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.chi.repository.RoomResidentRepository;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomResident;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.shared.export.TableExporter;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chi/exports")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class RoomTenantExportController {
    private final RoomRepository rooms;
    private final RoomResidentRepository residents;
    private final TableExporter exporter;

    public RoomTenantExportController(RoomRepository rooms, RoomResidentRepository residents, TableExporter exporter) {
        this.rooms = rooms;
        this.residents = residents;
        this.exporter = exporter;
    }

    /** UC125: danh sach phong va nguoi dang o, loc theo khu/tang/trang thai. */
    @GetMapping(value = "/rooms-tenants", produces = TableExporter.EXCEL_TYPE)
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Integer propertyId,
                                         @RequestParam(required = false) Integer floorId,
                                         @RequestParam(required = false) String status) {
        List<Room> selected = rooms.findAll().stream()
                .filter(r -> propertyId == null || Objects.equals(propertyId, r.getProperty().getPropertyId()))
                .filter(r -> floorId == null || Objects.equals(floorId, r.getFloor().getFloorId()))
                .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase(r.getStatus()))
                .toList();
        List<List<Object>> rows = selected.stream().flatMap(room -> {
            List<RoomResident> active = residents.findByRoomRoomId(room.getRoomId()).stream()
                    .filter(r -> !Boolean.FALSE.equals(r.getIsActive())).toList();
            if (active.isEmpty()) {
                return java.util.stream.Stream.of(List.<Object>of(room.getProperty().getPropertyName(), room.getFloor().getFloorName(),
                        room.getRoomCode(), room.getStatus(), "", ""));
            }
            return active.stream().map(r -> List.<Object>of(room.getProperty().getPropertyName(), room.getFloor().getFloorName(),
                    room.getRoomCode(), room.getStatus(), r.getFullName(), r.getPhoneNumber()));
        }).toList();
        byte[] body = exporter.toExcel(new TableData("Danh sách phòng và khách thuê",
                List.of("Khu trọ", "Tầng", "Phòng", "Trạng thái", "Người đang ở", "Số điện thoại"), rows));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=phong-va-khach-thue.xlsx").contentType(MediaType.parseMediaType(TableExporter.EXCEL_TYPE)).body(body);
    }
}
