package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.viet.repository.ContractNotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ContractExpiryDeliveryTest {

    @Test
    void schedulerDeliversEachTransitionWithoutRepeatingOnNextRun() {
        var repository = mock(ContractNotificationRepository.class);
        var notifications = mock(NotificationSender.class);
        var service = new ContractExpiryService(repository, notifications);
        var em = mock(EntityManager.class);
        ReflectionTestUtils.setField(service, "em", em);
        var expired = contract(1, LocalDate.now().minusDays(1));
        var expiring = contract(2, LocalDate.now().plusDays(15));
        var expireQuery = query(expired);
        var warnQuery = query(expiring);
        when(em.createQuery(contains("c.endDate < :today"), eq(RentalContract.class)))
                .thenReturn(expireQuery);
        when(em.createQuery(contains("c.endDate >= :today"), eq(RentalContract.class)))
                .thenReturn(warnQuery);

        var job = new VietJobs(service, mock(DebtService.class));
        job.refreshContracts();
        job.refreshContracts();

        assertEquals("EXPIRED", expired.getStatus());
        assertEquals("EXPIRING_SOON", expiring.getStatus());
        verify(notifications).notifyTenant(eq(5), eq("CONTRACT_EXPIRED"), anyString(), anyString(),
                anyMap(), eq("CONTRACT"), eq(1));
        verify(notifications).notifyTenant(eq(5), eq("CONTRACT_EXPIRING"), anyString(), anyString(),
                anyMap(), eq("CONTRACT"), eq(2));
        verify(notifications, times(2)).notifyStaff(eq(List.of("LANDLORD", "MANAGER")), anyString(),
                anyString(), anyString(), anyMap(), eq("CONTRACT"), anyInt());
        verify(repository, times(2)).save(any());
    }

    @SuppressWarnings("unchecked")
    private static TypedQuery<RentalContract> query(RentalContract contract) {
        TypedQuery<RentalContract> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(query);
        when(query.getResultList()).thenAnswer(call -> "ACTIVE".equals(contract.getStatus())
                ? List.of(contract) : List.of());
        return query;
    }

    private static RentalContract contract(int id, LocalDate end) {
        var tenant = new Tenant();
        tenant.setTenantId(5);
        tenant.setFullName("Tenant");
        var room = new Room();
        room.setRoomCode("P101");
        var contract = new RentalContract();
        contract.setContractId(id);
        contract.setContractCode("HD" + id);
        contract.setEndDate(end);
        contract.setStatus("ACTIVE");
        contract.setTenant(tenant);
        contract.setRoom(room);
        return contract;
    }
}
