package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomAddonFee;
import com.group7.minihouse.shared.entity.RoomEquipmentRequest;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.EquipmentDtos.InstallRequest;
import com.group7.minihouse.viet.repository.IncidentCategoryRepository;
import com.group7.minihouse.viet.repository.RoomAddonFeeRepository;
import com.group7.minihouse.viet.repository.RoomEquipmentRequestRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EquipmentInstallationTest {

    @Test
    void installCreatesOneFeeAndRemovalClosesIt() {
        var requests = mock(RoomEquipmentRequestRepository.class);
        var fees = mock(RoomAddonFeeRepository.class);
        var service = new EquipmentService(requests, fees, mock(IncidentCategoryRepository.class),
                mock(TenantRoomResolver.class), mock(ActorResolver.class));
        var room = new Room();
        room.setRoomId(7);
        var request = new RoomEquipmentRequest();
        request.setEquipmentRequestId(8);
        request.setRoom(room);
        var tenant = new Tenant();
        tenant.setTenantId(20);
        request.setTenant(tenant);
        request.setEquipmentName("Washing machine");
        request.setStatus("APPROVED");
        when(requests.findForUpdate(8)).thenReturn(Optional.of(request));
        when(requests.save(request)).thenReturn(request);
        var saved = org.mockito.ArgumentCaptor.forClass(RoomAddonFee.class);
        when(fees.save(any())).thenAnswer(call -> call.getArgument(0));

        service.install(8, new InstallRequest(null, new BigDecimal("150000"), 3));

        verify(fees).save(saved.capture());
        var fee = saved.getValue();
        assertEquals(3, fee.getBillingCycleMonths());
        assertTrue(fee.getIsActive());
        assertThrows(BusinessException.class, () -> service.install(8, null));

        when(fees.findByEquipmentRequest_EquipmentRequestId(8)).thenReturn(List.of(fee));
        assertEquals("REMOVED", service.remove(8).status());
        assertFalse(fee.getIsActive());
        assertNotNull(fee.getEndDate());
    }
}
