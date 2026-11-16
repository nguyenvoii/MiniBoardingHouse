package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.OccupancyDtos.ResidentRequest;
import com.group7.minihouse.phi.dto.OccupancyDtos.VehicleRequest;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomResident;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.TenantVehicle;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/** Xe cua khach thue va nguoi o cung: nguon so luong that cho dich vu tinh theo xe / dau nguoi (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OccupancyServiceTest {

    @Mock EntityManager em;
    @Mock ActivityLogService activityLogService;
    @Mock TypedQuery<TenantVehicle> vehicleQuery;
    @Mock TypedQuery<Long> countQuery;

    OccupancyService service;
    Tenant tenant;
    Room room;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new OccupancyService(em, activityLogService, clock);
        tenant = new Tenant();
        tenant.setTenantId(7);
        room = new Room();
        room.setRoomId(1);
        room.setRoomCode("P101");
        room.setMaxOccupants(3);
        when(em.find(Tenant.class, 7)).thenReturn(tenant);
        when(em.find(Room.class, 1)).thenReturn(room);
        when(em.createQuery(any(String.class), org.mockito.ArgumentMatchers.eq(TenantVehicle.class))).thenReturn(vehicleQuery);
        when(vehicleQuery.setParameter(any(String.class), any())).thenReturn(vehicleQuery);
        when(em.createQuery(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(any(String.class), any())).thenReturn(countQuery);
    }

    @Test
    void plateIsTrimmedUppercasedAndValidated() {
        assertEquals("59A1-123.45", OccupancyService.plate(new VehicleRequest("  59a1-123.45 ", null, null, null)));
        assertThrows(BusinessException.class, () -> OccupancyService.plate(new VehicleRequest("  ", null, null, null)));
        assertThrows(BusinessException.class, () -> OccupancyService.plate(new VehicleRequest("x".repeat(21), null, null, null)));
    }

    @Test
    void vehicleTypeDefaultsToMotorbikeAndRejectsUnknown() {
        assertEquals("MOTORBIKE", OccupancyService.type(new VehicleRequest("a", null, null, null)));
        assertEquals("CAR", OccupancyService.type(new VehicleRequest("a", "car", null, null)));
        assertThrows(BusinessException.class, () -> OccupancyService.type(new VehicleRequest("a", "ROCKET", null, null)));
    }

    @Test
    void addingNewVehiclePersistsItActive() {
        when(vehicleQuery.getResultList()).thenReturn(List.of());
        var r = service.addVehicle(7, new VehicleRequest("59a1-111", "MOTORBIKE", "Honda", "Do"), 5, "ip");
        assertEquals("59A1-111", r.licensePlate());
        verify(em).persist(any(TenantVehicle.class));
    }

    @Test
    void duplicateActivePlateIsRejected() {
        TenantVehicle existing = new TenantVehicle();
        existing.setLicensePlate("59A1-111");
        existing.setIsActive(true);
        existing.setTenant(tenant);
        when(vehicleQuery.getResultList()).thenReturn(List.of(existing));
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.addVehicle(7, new VehicleRequest("59A1-111", null, null, null), 5, "ip")).getStatus());
        verify(em, never()).persist(any());
    }

    @Test
    void inactivePlateIsReactivatedForTheNewOwner() {
        Tenant other = new Tenant();
        other.setTenantId(9);
        TenantVehicle old = new TenantVehicle();
        old.setVehicleId(3);
        old.setLicensePlate("59A1-111");
        old.setIsActive(false);
        old.setTenant(other);
        when(vehicleQuery.getResultList()).thenReturn(List.of(old));
        var r = service.addVehicle(7, new VehicleRequest("59A1-111", null, null, null), 5, "ip");
        assertEquals(7, r.tenantId());
        assertEquals(true, old.getIsActive());
        verify(em, never()).persist(any());
    }

    @Test
    void removingVehicleOnlyDeactivatesIt() {
        TenantVehicle v = new TenantVehicle();
        v.setLicensePlate("59A1-111");
        v.setIsActive(true);
        v.setTenant(tenant);
        when(em.find(TenantVehicle.class, 3)).thenReturn(v);
        service.removeVehicle(3, 5, "ip");
        assertEquals(false, v.getIsActive());
    }

    @Test
    void residentNeedsNameAndRespectsRoomCapacity() {
        assertThrows(BusinessException.class, () -> OccupancyService.name(new ResidentRequest(" ", null, null, null, null)));
        when(countQuery.getSingleResult()).thenReturn(2L); // da co 2 nguoi o cung + nguoi dung ten hop dong = 3 = toi da
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.addResident(1, new ResidentRequest("Be", null, null, "Con", null), 5, "ip")).getStatus());
        verify(em, never()).persist(any());
    }

    @Test
    void residentIsAddedWhenThereIsRoomAndStartsToday() {
        when(countQuery.getSingleResult()).thenReturn(1L);
        var r = service.addResident(1, new ResidentRequest(" Tran B ", "0900", null, "Ban be", null), 5, "ip");
        assertEquals("Tran B", r.fullName());
        assertEquals(LocalDate.of(2026, 11, 16), r.startDate());
        verify(em).persist(any(RoomResident.class));
    }

    @Test
    void removingResidentEndsStayToday() {
        RoomResident r = new RoomResident();
        r.setResidentId(5);
        r.setRoom(room);
        r.setFullName("Tran B");
        r.setIsActive(true);
        when(em.find(RoomResident.class, 5)).thenReturn(r);
        service.removeResident(5, 5, "ip");
        assertEquals(false, r.getIsActive());
        assertEquals(LocalDate.of(2026, 11, 16), r.getEndDate());
    }
}
