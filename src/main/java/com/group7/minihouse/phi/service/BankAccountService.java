package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.BankAccountRequest;
import com.group7.minihouse.phi.dto.BankAccountResponse;
import com.group7.minihouse.phi.repository.BankAccountRepository;
import com.group7.minihouse.shared.entity.BankAccount;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.storage.FileStorage;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC119 tai khoan ngan hang va ma QR nhan tien theo khu tro. Moi khu tro co 1 tai khoan mac dinh trong so
 * cac tai khoan dang dung; xoa = ngung dung (giu dong trong DB vi giao dich thanh toan co the tham chieu).
 * Chi doc bang properties bang EntityManager (bang cua Chi), khong viet repository cho bang do.
 */
@Service
public class BankAccountService {

    public static final String QR_FOLDER = "qr";
    public static final String QR_URL_PREFIX = "/api/bank-accounts/qr/";
    static final long QR_MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern NUMBER = Pattern.compile("[0-9]{6,30}");

    private final BankAccountRepository repo;
    private final EntityManager em;
    private final FileStorage fileStorage;
    private final ActivityLogService activityLogService;

    public BankAccountService(BankAccountRepository repo, EntityManager em, FileStorage fileStorage,
                              ActivityLogService activityLogService) {
        this.repo = repo;
        this.em = em;
        this.fileStorage = fileStorage;
        this.activityLogService = activityLogService;
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> list(Integer propertyId) {
        property(propertyId);
        return repo.findActiveByProperty(propertyId).stream().map(BankAccountService::toResponse).toList();
    }

    @Transactional
    public BankAccountResponse create(Integer propertyId, BankAccountRequest req, Integer actorId, String ip) {
        Property property = property(propertyId);
        Fields f = validate(req);
        List<BankAccount> active = repo.findActiveByProperty(propertyId);
        rejectDuplicate(active, null, f);

        BankAccount b = new BankAccount();
        b.setProperty(property);
        f.applyTo(b);
        b.setIsDefault(active.isEmpty()); // tai khoan dau tien cua khu tro tu thanh mac dinh
        b.setIsActive(true);
        BankAccount saved = repo.save(b);
        activityLogService.log(actorId, "CREATE_BANK_ACCOUNT",
                "Them tai khoan ngan hang vao khu tro " + propertyId, ip);
        return toResponse(saved);
    }

    @Transactional
    public BankAccountResponse update(Integer id, BankAccountRequest req, Integer actorId, String ip) {
        BankAccount b = load(id);
        Fields f = validate(req);
        rejectDuplicate(repo.findActiveByProperty(b.getProperty().getPropertyId()), id, f);
        f.applyTo(b);
        repo.save(b);
        activityLogService.log(actorId, "UPDATE_BANK_ACCOUNT", "Sua tai khoan ngan hang " + id, ip);
        return toResponse(b);
    }

    @Transactional
    public BankAccountResponse setDefault(Integer id, Integer actorId, String ip) {
        BankAccount b = load(id);
        if (!Boolean.TRUE.equals(b.getIsActive())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Tài khoản đã ngừng dùng, không đặt làm mặc định được");
        }
        for (BankAccount other : repo.findActiveByProperty(b.getProperty().getPropertyId())) {
            other.setIsDefault(other.getBankAccountId().equals(id));
            repo.save(other);
        }
        activityLogService.log(actorId, "SET_DEFAULT_BANK_ACCOUNT", "Dat tai khoan ngan hang mac dinh " + id, ip);
        return toResponse(b);
    }

    /** Ngung dung tai khoan. Neu la tai khoan mac dinh thi tai khoan dang dung dau tien con lai len thay. */
    @Transactional
    public void deactivate(Integer id, Integer actorId, String ip) {
        BankAccount b = load(id);
        boolean wasDefault = Boolean.TRUE.equals(b.getIsDefault());
        b.setIsActive(false);
        b.setIsDefault(false);
        repo.save(b);
        if (wasDefault) {
            repo.findActiveByProperty(b.getProperty().getPropertyId()).stream()
                    .filter(o -> !o.getBankAccountId().equals(id)).findFirst().ifPresent(o -> {
                        o.setIsDefault(true);
                        repo.save(o);
                    });
        }
        activityLogService.log(actorId, "DEACTIVATE_BANK_ACCOUNT", "Ngung dung tai khoan ngan hang " + id, ip);
    }

    /** Dat anh ma QR (PNG/JPG/WebP toi da 2 MB), thay anh cu neu co. */
    @Transactional
    public BankAccountResponse setQr(Integer id, byte[] image, Integer actorId, String ip) {
        BankAccount b = load(id);
        String name = fileStorage.saveImage(QR_FOLDER, image, QR_MAX_BYTES);
        String old = b.getQrImageUrl();
        b.setQrImageUrl(QR_URL_PREFIX + name);
        repo.save(b);
        if (old != null && old.startsWith(QR_URL_PREFIX)) {
            fileStorage.delete(QR_FOLDER, old.substring(QR_URL_PREFIX.length()));
        }
        activityLogService.log(actorId, "UPDATE_BANK_QR", "Cap nhat ma QR cua tai khoan " + id, ip);
        return toResponse(b);
    }

    // ------------------------------------------------------------------ noi bo

    private Property property(Integer propertyId) {
        Property p = em.find(Property.class, propertyId);
        if (p == null) {
            throw BusinessException.notFound("Khu trọ");
        }
        return p;
    }

    private BankAccount load(Integer id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("Tài khoản ngân hàng"));
    }

    private static Fields validate(BankAccountRequest req) {
        String bank = req.bankName() == null ? "" : req.bankName().trim();
        String number = req.accountNumber() == null ? "" : req.accountNumber().trim();
        String holder = req.accountHolder() == null ? "" : req.accountHolder().trim();
        if (bank.isEmpty() || bank.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Tên ngân hàng không được để trống và tối đa 100 ký tự");
        }
        if (!NUMBER.matcher(number).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số tài khoản chỉ gồm 6-30 chữ số");
        }
        if (holder.isEmpty() || holder.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Tên chủ tài khoản không được để trống và tối đa 100 ký tự");
        }
        return new Fields(bank, number, holder);
    }

    private static void rejectDuplicate(List<BankAccount> active, Integer selfId, Fields f) {
        boolean dup = active.stream().anyMatch(o -> !o.getBankAccountId().equals(selfId)
                && o.getBankName().equalsIgnoreCase(f.bank()) && o.getAccountNumber().equals(f.number()));
        if (dup) {
            throw new BusinessException(HttpStatus.CONFLICT, "Khu trọ đã có tài khoản này");
        }
    }

    private static BankAccountResponse toResponse(BankAccount b) {
        return new BankAccountResponse(b.getBankAccountId(), b.getProperty().getPropertyId(), b.getBankName(),
                b.getAccountNumber(), b.getAccountHolder(), b.getQrImageUrl(),
                Boolean.TRUE.equals(b.getIsDefault()), Boolean.TRUE.equals(b.getIsActive()));
    }

    private record Fields(String bank, String number, String holder) {
        void applyTo(BankAccount b) {
            b.setBankName(bank);
            b.setAccountNumber(number);
            b.setAccountHolder(holder);
        }
    }
}
