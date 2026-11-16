package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.BankAccountRequest;
import com.group7.minihouse.phi.dto.BankAccountResponse;
import com.group7.minihouse.phi.repository.BankAccountRepository;
import com.group7.minihouse.shared.entity.BankAccount;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.storage.FileStorage;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** UC119 tai khoan ngan hang + ma QR nhan tien theo khu tro (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock BankAccountRepository repo;
    @Mock EntityManager em;
    @Mock FileStorage fileStorage;
    @Mock ActivityLogService activityLogService;

    BankAccountService service;
    Property property;

    @BeforeEach
    void setUp() {
        service = new BankAccountService(repo, em, fileStorage, activityLogService);
        property = new Property();
        property.setPropertyId(1);
    }

    private BankAccount acc(int id, boolean def, boolean active) {
        BankAccount b = new BankAccount();
        b.setBankAccountId(id);
        b.setProperty(property);
        b.setBankName("Vietcombank");
        b.setAccountNumber("0011001234567");
        b.setAccountHolder("NGUYEN VAN A");
        b.setIsDefault(def);
        b.setIsActive(active);
        return b;
    }

    private BankAccountRequest req(String bank, String number, String holder) {
        return new BankAccountRequest(bank, number, holder);
    }

    private void propertyExists() {
        when(em.find(Property.class, 1)).thenReturn(property);
    }

    @Test
    void firstAccountOfPropertyBecomesDefault() {
        propertyExists();
        when(repo.findActiveByProperty(1)).thenReturn(List.of());
        when(repo.save(any(BankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        BankAccountResponse r = service.create(1, req(" Vietcombank ", "0011001234567", "NGUYEN VAN A"), 2, "ip");

        assertTrue(r.isDefault());
        assertTrue(r.isActive());
        assertEquals("Vietcombank", r.bankName());
        verify(activityLogService).log(2, "CREATE_BANK_ACCOUNT", "Them tai khoan ngan hang vao khu tro 1", "ip");
    }

    @Test
    void secondAccountIsNotDefault() {
        propertyExists();
        when(repo.findActiveByProperty(1)).thenReturn(List.of(acc(10, true, true)));
        when(repo.save(any(BankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        BankAccountResponse r = service.create(1, req("ACB", "123456789", "NGUYEN VAN A"), 2, "ip");

        assertFalse(r.isDefault());
    }

    @Test
    void createRejectsUnknownPropertyBadInputAndDuplicate() {
        when(em.find(Property.class, 99)).thenReturn(null);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.create(99, req("ACB", "123456789", "A"), 2, "ip")).getStatus());

        propertyExists();
        for (BankAccountRequest bad : List.of(req("", "123456789", "A"), req("ACB", "12ab", "A"),
                req("ACB", "123456789", " "), req("ACB", "123", "A"))) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.create(1, bad, 2, "ip")).getStatus());
        }

        when(repo.findActiveByProperty(1)).thenReturn(List.of(acc(10, true, true)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.create(1, req("vietcombank", "0011001234567", "B"), 2, "ip")).getStatus());
        verify(repo, never()).save(any());
    }

    @Test
    void setDefaultMovesTheFlag() {
        BankAccount a = acc(10, true, true);
        BankAccount b = acc(11, false, true);
        when(repo.findById(11)).thenReturn(Optional.of(b));
        when(repo.findActiveByProperty(1)).thenReturn(List.of(a, b));

        service.setDefault(11, 2, "ip");

        assertFalse(a.getIsDefault());
        assertTrue(b.getIsDefault());
    }

    @Test
    void cannotSetInactiveAccountAsDefault() {
        when(repo.findById(11)).thenReturn(Optional.of(acc(11, false, false)));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.setDefault(11, 2, "ip")).getStatus());
    }

    @Test
    void deactivatingDefaultPromotesAnotherActiveAccount() {
        BankAccount a = acc(10, true, true);
        BankAccount b = acc(11, false, true);
        when(repo.findById(10)).thenReturn(Optional.of(a));
        when(repo.findActiveByProperty(1)).thenReturn(new ArrayList<>(List.of(a, b)));

        service.deactivate(10, 2, "ip");

        assertFalse(a.getIsActive());
        assertFalse(a.getIsDefault());
        assertTrue(b.getIsDefault());
    }

    @Test
    void updateChangesFieldsAndRejectsDuplicateOfAnotherAccount() {
        BankAccount a = acc(10, true, true);
        BankAccount other = acc(11, false, true);
        other.setAccountNumber("999888777");
        when(repo.findById(10)).thenReturn(Optional.of(a));
        when(repo.findActiveByProperty(1)).thenReturn(List.of(a, other));

        service.update(10, req("Vietcombank", "5555666677", "NGUYEN VAN B"), 2, "ip");
        assertEquals("5555666677", a.getAccountNumber());
        assertEquals("NGUYEN VAN B", a.getAccountHolder());

        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.update(10, req("Vietcombank", "999888777", "X"), 2, "ip")).getStatus());
    }

    @Test
    void setQrStoresImageAndReplacesOldOne() {
        BankAccount a = acc(10, true, true);
        a.setQrImageUrl("/api/bank-accounts/qr/old.png");
        when(repo.findById(10)).thenReturn(Optional.of(a));
        byte[] img = {1, 2};
        when(fileStorage.saveImage("qr", img, 2 * 1024 * 1024)).thenReturn("new.png");

        BankAccountResponse r = service.setQr(10, img, 2, "ip");

        assertEquals("/api/bank-accounts/qr/new.png", r.qrImageUrl());
        verify(fileStorage).delete("qr", "old.png");
    }

    @Test
    void listReturnsActiveAccountsOfProperty() {
        propertyExists();
        when(repo.findActiveByProperty(1)).thenReturn(List.of(acc(10, true, true), acc(11, false, true)));
        assertEquals(2, service.list(1).size());
    }
}
