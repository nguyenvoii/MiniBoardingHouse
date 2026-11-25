package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.ContractDtos.AdjustRequest;
import com.group7.minihouse.phi.dto.ContractDtos.AdjustmentResponse;
import com.group7.minihouse.phi.dto.ContractDtos.ContractDetail;
import com.group7.minihouse.phi.dto.ContractDtos.ContractResponse;
import com.group7.minihouse.phi.dto.ContractDtos.CreateContractRequest;
import com.group7.minihouse.phi.dto.ContractDtos.ExtendRequest;
import com.group7.minihouse.phi.dto.ContractDtos.PaymentTermsRequest;
import com.group7.minihouse.phi.dto.ContractDtos.PricingRequest;
import com.group7.minihouse.phi.dto.ContractDtos.TerminateRequest;
import com.group7.minihouse.phi.dto.ContractDtos.TransferRequest;
import com.group7.minihouse.phi.dto.ContractDtos.UtilityTermsRequest;
import com.group7.minihouse.phi.repository.ContractAdjustmentRepository;
import com.group7.minihouse.phi.repository.RentalContractRepository;
import com.group7.minihouse.phi.repository.TenantReadRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.ContractAdjustment;
import com.group7.minihouse.shared.entity.ContractTemplate;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.ResidentStayHistory;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserNotification;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableExporter;
import com.group7.minihouse.shared.storage.FileStorage;
import com.group7.minihouse.shared.util.DepositLedger;
import com.group7.minihouse.toan.dto.MeterDtos.MoveOutRequest;
import com.group7.minihouse.toan.service.MeterReadingService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC41-51: hop dong thue phong. Trang thai: ACTIVE, EXPIRING_SOON, EXPIRED, TERMINATED (xem CHECK trong DB).
 * Phong va khach thue la bang cua Chi nen doc/ghi qua EntityManager (Chi chua co service).
 * Moi thay doi quan trong ghi 1 dong contract_adjustments de tra cuoc lich su (UC47).
 */
@Service
public class ContractService {

    public static final String SIGNATURE_FOLDER = "signatures";
    public static final String SIGNATURE_URL_PREFIX = "/api/contracts/signatures/";
    static final long SIGNATURE_MAX_BYTES = 1024 * 1024;
    private static final Set<String> LIVE = Set.of("ACTIVE", "EXPIRING_SOON");
    /** Khách còn chiếm phòng: hợp đồng hết hạn (EXPIRED) mà chưa trả phòng/chấm dứt vẫn phải kết thúc được. */
    private static final Set<String> OCCUPYING = Set.of("ACTIVE", "EXPIRING_SOON", "EXPIRED");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "EXPIRING_SOON", "EXPIRED", "TERMINATED");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RentalContractRepository repo;
    private final ContractAdjustmentRepository adjustmentRepo;
    private final TenantReadRepository tenantRepo;
    private final UserRepository userRepo;
    private final EntityManager em;
    private final FileStorage fileStorage;
    private final TableExporter exporter;
    private final MeterReadingService meterService;
    private final ActivityLogService activityLogService;
    private final ContractEndService endService;
    private final DepositService depositService;

    public ContractService(RentalContractRepository repo, ContractAdjustmentRepository adjustmentRepo,
                           TenantReadRepository tenantRepo, UserRepository userRepo, EntityManager em,
                           FileStorage fileStorage, TableExporter exporter, MeterReadingService meterService,
                           ActivityLogService activityLogService, ContractEndService endService,
                           DepositService depositService) {
        this.endService = endService;
        this.depositService = depositService;
        this.repo = repo;
        this.adjustmentRepo = adjustmentRepo;
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
        this.em = em;
        this.fileStorage = fileStorage;
        this.exporter = exporter;
        this.meterService = meterService;
        this.activityLogService = activityLogService;
    }

    // ------------------------------------------------------------------ UC41: lap hop dong

    @Transactional
    public ContractResponse create(CreateContractRequest req, Integer actorId, String ip) {
        if (req == null || req.roomId() == null || req.tenantId() == null
                || req.startDate() == null || req.endDate() == null) {
            throw bad("Can roomId, tenantId, startDate, endDate");
        }
        if (!req.endDate().isAfter(req.startDate())) {
            throw bad("Ngay ket thuc phai sau ngay bat dau");
        }
        Room room = room(req.roomId());
        em.refresh(room, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var hold = room.activeHold(LocalDateTime.now());
        if (hold != null && !hold.getBookingId().equals(req.bookingId())) {
            throw conflict("Phòng đang được giữ chỗ. Hãy chọn đúng yêu cầu giữ chỗ khi lập hợp đồng.");
        }
        Tenant tenant = tenant(req.tenantId());
        if (!"VACANT".equals(room.getStatus())) {
            throw conflict("Phong " + room.getRoomCode() + " khong o trang thai trong (VACANT)");
        }
        if (repo.countLiveByRoom(room.getRoomId(), 0) > 0) {
            throw conflict("Phong da co hop dong con hieu luc");
        }
        ContractTemplate template = null;
        if (req.templateId() != null) {
            template = em.find(ContractTemplate.class, req.templateId());
            if (template == null) {
                throw BusinessException.notFound("Mau hop dong " + req.templateId());
            }
        }
        BigDecimal rent = req.monthlyRentPrice() != null ? req.monthlyRentPrice() : room.getBaseRentPrice();
        BigDecimal deposit = req.depositAmount() != null ? req.depositAmount() : room.getDepositAmount();
        int cycle = req.paymentCycleMonths() != null ? req.paymentCycleMonths() : 1;
        int dueDay = req.paymentDueDay() != null ? req.paymentDueDay() : 5;
        checkPricing(rent, deposit, cycle, dueDay);
        if (req.lateFeePerDay() != null && req.lateFeePerDay().signum() < 0) {
            throw bad("Tien phat tre han moi ngay khong duoc am");
        }

        RentalContract c = new RentalContract();
        c.setContractCode(newCode(room, req.startDate()));
        c.setRoom(room);
        c.setTenant(tenant);
        c.setTemplate(template);
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        c.setMonthlyRentPrice(rent);
        c.setDepositAmount(deposit);
        c.setPaymentCycleMonths(cycle);
        c.setPaymentDueDay(dueDay);
        c.setLateFeePerDay(req.lateFeePerDay() != null ? req.lateFeePerDay() : BigDecimal.ZERO);
        c.setStatus("ACTIVE");
        c.setCreatedAt(LocalDateTime.now());
        RentalContract saved = repo.save(c);
        saved.setPdfContractUrl("/api/contracts/" + saved.getContractId() + "/pdf");
        room.setStatus("RENTED");
        audit(actorId, "CREATE_CONTRACT", "Lap hop dong " + saved.getContractCode(), ip);
        if (req.bookingId() != null) {
            linkBooking(req.bookingId(), saved, actorId, ip);
        }
        return toResponse(saved);
    }

    /**
     * M5: noi yeu cau giu cho (Tiep nhan) voi hop dong: phai la yeu cau APPROVED cua dung khach va phong. Tien giu cho
     * da nhan (deposit_paid) thanh phieu thu coc cua hop dong (toi da bang tien coc), yeu cau duoc dong lai de khong
     * dung lai lan hai (bang khong co trang thai rieng nen dung CANCELLED kem ghi chu ma hop dong).
     */
    private void linkBooking(Integer bookingId, RentalContract c, Integer actorId, String ip) {
        com.group7.minihouse.shared.entity.BookingRequest b =
                em.find(com.group7.minihouse.shared.entity.BookingRequest.class, bookingId);
        if (b == null) {
            throw BusinessException.notFound("Yeu cau giu cho " + bookingId);
        }
        if (!"APPROVED".equals(b.getStatus())) {
            throw conflict("Chi lap hop dong tu yeu cau giu cho da duyet (hien la " + b.getStatus() + ")");
        }
        if (!b.getRoom().getRoomId().equals(c.getRoom().getRoomId())
                || !b.getTenant().getTenantId().equals(c.getTenant().getTenantId())) {
            throw bad("Yeu cau giu cho khong thuoc phong va khach da chon");
        }
        if (b.getHoldUntil() != null && !b.getHoldUntil().isAfter(LocalDateTime.now())) {
            throw conflict("Yêu cầu giữ chỗ đã hết hạn");
        }
        BigDecimal held = b.getDepositPaid() == null ? BigDecimal.ZERO : b.getDepositPaid();
        BigDecimal toCollect = held.min(c.getDepositAmount());
        if (toCollect.signum() > 0) {
            depositService.collect(c.getContractId(), toCollect, actorId, ip);
        }
        b.setStatus("CANCELLED");
        b.setRejectionReason("Da lap hop dong " + c.getContractCode());
    }

    // ------------------------------------------------------------------ UC47: tra cuu

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> search(String keyword, String status, int page, int size) {
        String st = status == null ? "" : status.trim();
        if (!st.isEmpty() && !STATUSES.contains(st)) {
            throw bad("Trang thai khong hop le (ACTIVE, EXPIRING_SOON, EXPIRED, TERMINATED)");
        }
        int s = size < 1 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(Math.min(Math.max(page, 0), 100_000), s,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "contractId")));
        return new PageResponse<>(repo.search(likePattern(keyword), st, pageable).map(ContractService::toResponse));
    }

    @Transactional(readOnly = true)
    public ContractDetail detail(Integer id) {
        RentalContract c = load(id);
        List<AdjustmentResponse> adj = adjustmentRepo
                .findByContractContractIdOrderByAdjustedAtDescAdjustmentIdDesc(id).stream()
                .map(ContractService::toResponse).toList();
        return new ContractDetail(toResponse(c), adj);
    }

    /** Hop dong cua chinh nguoi thue (UC37). */
    @Transactional(readOnly = true)
    public List<ContractResponse> mine(Integer tenantId) {
        return repo.findByTenantTenantIdOrderByStartDateDesc(tenantId).stream()
                .map(ContractService::toResponse).toList();
    }

    /** Nguoi thue chi duoc xem hop dong cua minh (dung cho /api/me/contracts/{id}). */
    @Transactional(readOnly = true)
    public ContractDetail detailOfTenant(Integer id, Integer tenantId) {
        RentalContract c = load(id);
        if (!c.getTenant().getTenantId().equals(tenantId)) {
            throw BusinessException.notFound("Hop dong " + id);
        }
        return detail(id);
    }

    // ------------------------------------------------------------------ UC42-44: dieu khoan

    @Transactional
    public ContractResponse updatePricing(Integer id, PricingRequest req, Integer actorId, String ip) {
        RentalContract c = liveContract(id);
        BigDecimal rent = req.monthlyRentPrice() != null ? req.monthlyRentPrice() : c.getMonthlyRentPrice();
        BigDecimal deposit = req.depositAmount() != null ? req.depositAmount() : c.getDepositAmount();
        int cycle = req.paymentCycleMonths() != null ? req.paymentCycleMonths() : c.getPaymentCycleMonths();
        int dueDay = req.paymentDueDay() != null ? req.paymentDueDay() : c.getPaymentDueDay();
        checkPricing(rent, deposit, cycle, dueDay);
        if (rent.compareTo(c.getMonthlyRentPrice()) != 0) {
            record(c, "PRICE_CHANGE", c.getMonthlyRentPrice().toPlainString(), rent.toPlainString(), actorId);
        }
        if (deposit.compareTo(c.getDepositAmount()) < 0) {
            // M2: ha coc xuong duoi so da thu thi khach mat phan chenh khi hoan coc; phai hoan phan do truoc
            BigDecimal collected = DepositLedger.collected(em, c.getContractCode());
            if (deposit.compareTo(collected) < 0) {
                throw conflict("Da thu coc " + collected.toBigInteger() + ", khong the sua tien coc xuong "
                        + deposit.toBigInteger() + " (thap hon so da thu). Hoan phan chenh cho khach truoc");
            }
        }
        if (deposit.compareTo(c.getDepositAmount()) != 0) {
            record(c, "DEPOSIT_CHANGE", c.getDepositAmount().toPlainString(), deposit.toPlainString(), actorId);
        }
        if (cycle != c.getPaymentCycleMonths() || dueDay != c.getPaymentDueDay()) {
            record(c, "PAYMENT_SCHEDULE", c.getPaymentCycleMonths() + " thang/lan, ngay " + c.getPaymentDueDay(),
                    cycle + " thang/lan, ngay " + dueDay, actorId);
        }
        c.setMonthlyRentPrice(rent);
        c.setDepositAmount(deposit);
        c.setPaymentCycleMonths(cycle);
        c.setPaymentDueDay(dueDay);
        audit(actorId, "UPDATE_CONTRACT_PRICING", "Sua gia/coc/chu ky hop dong " + c.getContractCode(), ip);
        return toResponse(c);
    }

    @Transactional
    public ContractResponse updatePaymentTerms(Integer id, PaymentTermsRequest req, Integer actorId, String ip) {
        RentalContract c = liveContract(id);
        if (req.lateFeePerDay() != null) {
            if (req.lateFeePerDay().signum() < 0) {
                throw bad("Tien phat tre han moi ngay khong duoc am");
            }
            record(c, "PAYMENT_TERMS", str(c.getLateFeePerDay()), req.lateFeePerDay().toPlainString(), actorId);
            c.setLateFeePerDay(req.lateFeePerDay());
        }
        if (req.depositRefundConditions() != null) {
            c.setDepositRefundConditions(req.depositRefundConditions().trim());
        }
        audit(actorId, "UPDATE_CONTRACT_TERMS", "Sua dieu khoan thanh toan hop dong " + c.getContractCode(), ip);
        return toResponse(c);
    }

    @Transactional
    public ContractResponse updateUtilityTerms(Integer id, UtilityTermsRequest req, Integer actorId, String ip) {
        RentalContract c = liveContract(id);
        if (req.electricTerms() != null) {
            record(c, "UTILITY_TERMS", c.getElectricTerms(), limit(req.electricTerms()), actorId);
            c.setElectricTerms(req.electricTerms().trim());
        }
        if (req.waterTerms() != null) {
            c.setWaterTerms(req.waterTerms().trim());
        }
        audit(actorId, "UPDATE_CONTRACT_UTILITY", "Sua dieu khoan dien nuoc hop dong " + c.getContractCode(), ip);
        return toResponse(c);
    }

    // ------------------------------------------------------------------ UC45: PDF

    /** Tao noi dung PDF theo mau (khong luu file; moi lan tai se dung lai du lieu moi nhat). */
    @Transactional(readOnly = true) // SRS 13#11: GET khong ghi DB; pdf_contract_url duoc dat luc lap hop dong
    public byte[] pdf(Integer id, Integer actorId) {
        RentalContract c = load(id);
        return exporter.toTextPdf("HOP DONG THUE PHONG TRO", pdfLines(c));
    }

    List<String> pdfLines(RentalContract c) {
        Tenant t = c.getTenant();
        List<String> l = new ArrayList<>();
        l.add("So hop dong: " + c.getContractCode());
        l.add("");
        l.add("BEN CHO THUE (Chu tro) va BEN THUE:");
        l.add("Ben thue: " + t.getFullName() + " - CMND/CCCD: " + t.getIdentityNumber());
        l.add("Dia chi thuong tru: " + nz(t.getPermanentAddress()) + " - SDT: " + t.getPhoneNumber());
        l.add("");
        l.add("Phong thue: " + c.getRoom().getRoomCode());
        l.add("Thoi han: tu " + c.getStartDate().format(DATE) + " den " + c.getEndDate().format(DATE));
        l.add("Gia thue: " + money(c.getMonthlyRentPrice()) + " VND/thang");
        l.add("Tien coc: " + money(c.getDepositAmount()) + " VND");
        l.add("Thanh toan: " + c.getPaymentCycleMonths() + " thang/lan, han ngay " + c.getPaymentDueDay()
                + " hang ky");
        l.add("Phat tre han: " + money(c.getLateFeePerDay()) + " VND/ngay");
        if (c.getElectricTerms() != null) l.add("Dieu khoan dien: " + c.getElectricTerms());
        if (c.getWaterTerms() != null) l.add("Dieu khoan nuoc: " + c.getWaterTerms());
        if (c.getDepositRefundConditions() != null) l.add("Dieu kien hoan coc: " + c.getDepositRefundConditions());
        if (c.getTemplate() != null) {
            l.add("");
            l.add("DIEU KHOAN CHUNG:");
            l.addAll(List.of(c.getTemplate().getTermsContent().split("\\R")));
        }
        l.add("");
        l.add("Trang thai chu ky dien tu: " + (c.getDigitalSignatureUrl() == null ? "chua ky" : "da ky"));
        return l;
    }

    // ------------------------------------------------------------------ UC46: gui + ky

    /** Gui thong bao trong ung dung cho tai khoan cua khach thue (tim theo so dien thoai); khong co tai khoan thi bao ro. */
    @Transactional
    public String send(Integer id, Integer actorId, String ip) {
        RentalContract c = liveContract(id);
        User user = userRepo.findByPhoneNumber(c.getTenant().getPhoneNumber().trim()).orElse(null);
        audit(actorId, "SEND_CONTRACT", "Gui hop dong " + c.getContractCode() + " cho khach thue", ip);
        if (user == null) {
            return "Khach thue chua co tai khoan, hay cap tai khoan (UC10) hoac dua ban PDF truc tiep";
        }
        UserNotification n = new UserNotification();
        n.setUser(user);
        n.setTitle("Hop dong cho ky");
        n.setContent("Vui long xem va ky hop dong " + c.getContractCode());
        n.setReferenceType("CONTRACT");
        n.setReferenceId(c.getContractId());
        n.setIsRead(false);
        em.persist(n);
        return "Da gui thong bao cho khach thue";
    }

    /** Luu anh chu ky; chi ky 1 lan (ky lai phai cham dut hop dong va lap moi). */
    @Transactional
    public ContractResponse sign(Integer id, byte[] image, Integer actorId, String ip) {
        RentalContract c = liveContract(id);
        if (c.getDigitalSignatureUrl() != null) {
            throw conflict("Hop dong da duoc ky");
        }
        String name = fileStorage.saveImage(SIGNATURE_FOLDER, image, SIGNATURE_MAX_BYTES);
        c.setDigitalSignatureUrl(SIGNATURE_URL_PREFIX + name);
        record(c, "SIGNED", null, c.getDigitalSignatureUrl(), actorId);
        audit(actorId, "SIGN_CONTRACT", "Ky hop dong " + c.getContractCode(), ip);
        return toResponse(c);
    }

    /** Nguoi thue chi duoc ky hop dong cua minh. */
    @Transactional
    public ContractResponse signByTenant(Integer id, Integer tenantId, byte[] image, Integer actorId, String ip) {
        if (!load(id).getTenant().getTenantId().equals(tenantId)) {
            throw BusinessException.notFound("Hop dong " + id);
        }
        return sign(id, image, actorId, ip);
    }

    // ------------------------------------------------------------------ UC48-49: gia han, dieu chinh

    @Transactional
    public ContractResponse extend(Integer id, ExtendRequest req, Integer actorId, String ip) {
        RentalContract c = load(id);
        if ("TERMINATED".equals(c.getStatus())) {
            throw conflict("Hop dong da cham dut, khong gia han duoc");
        }
        if (req == null || req.newEndDate() == null || !req.newEndDate().isAfter(c.getEndDate())) {
            throw bad("Ngay het han moi phai sau ngay het han hien tai (" + c.getEndDate() + ")");
        }
        if ("EXPIRED".equals(c.getStatus())) {
            // hop dong da het han: chi gia han lai neu phong chua cho ai thue
            String roomStatus = c.getRoom().getStatus();
            if (repo.countLiveByRoom(c.getRoom().getRoomId(), c.getContractId()) > 0
                    || !("VACANT".equals(roomStatus) || "RENTED".equals(roomStatus))) {
                throw conflict("Phong da co hop dong khac, khong gia han duoc");
            }
            c.getRoom().setStatus("RENTED");
        }
        record(c, "EXTENSION", c.getEndDate().toString(), req.newEndDate().toString(), actorId);
        c.setEndDate(req.newEndDate());
        c.setStatus("ACTIVE");
        audit(actorId, "EXTEND_CONTRACT", "Gia han hop dong " + c.getContractCode() + " den " + req.newEndDate(), ip);
        return toResponse(c);
    }

    @Transactional
    public ContractResponse adjust(Integer id, AdjustRequest req, Integer actorId, String ip) {
        RentalContract c = liveContract(id);
        if (req == null || req.monthlyRentPrice() == null && req.tenantId() == null) {
            throw bad("Can monthlyRentPrice hoac tenantId");
        }
        if (req.monthlyRentPrice() != null) {
            if (req.monthlyRentPrice().signum() <= 0) {
                throw bad("Gia thue phai lon hon 0");
            }
            if (req.monthlyRentPrice().compareTo(c.getMonthlyRentPrice()) != 0) {
                record(c, "PRICE_CHANGE", c.getMonthlyRentPrice().toPlainString(),
                        req.monthlyRentPrice().toPlainString(), actorId);
                c.setMonthlyRentPrice(req.monthlyRentPrice());
            }
        }
        if (req.tenantId() != null && !req.tenantId().equals(c.getTenant().getTenantId())) {
            Tenant newTenant = tenant(req.tenantId());
            record(c, "TENANT_CHANGE", String.valueOf(c.getTenant().getTenantId()),
                    String.valueOf(newTenant.getTenantId()), actorId);
            c.setTenant(newTenant);
            c.setDigitalSignatureUrl(null); // doi nguoi thi phai ky lai
        }
        audit(actorId, "ADJUST_CONTRACT", "Dieu chinh hop dong " + c.getContractCode(), ip);
        return toResponse(c);
    }

    // ------------------------------------------------------------------ UC50: chuyen phong

    @Transactional
    public ContractResponse transferRoom(Integer id, TransferRequest req, Integer actorId, String ip) {
        RentalContract c = liveContract(id);
        if (req == null || req.newRoomId() == null) {
            throw bad("Can newRoomId");
        }
        Room oldRoom = c.getRoom();
        if (oldRoom.getRoomId().equals(req.newRoomId())) {
            throw bad("Phong moi trung phong hien tai");
        }
        Room newRoom = room(req.newRoomId());
        em.refresh(newRoom, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (newRoom.activeHold(LocalDateTime.now()) != null) {
            throw conflict("Phòng đang được giữ chỗ");
        }
        if (!"VACANT".equals(newRoom.getStatus()) || repo.countLiveByRoom(newRoom.getRoomId(), 0) > 0) {
            throw conflict("Phong " + newRoom.getRoomCode() + " khong trong");
        }
        boolean closing = req.periodId() != null || req.closingElectric() != null || req.closingWater() != null;
        if (closing && (req.periodId() == null || req.closingElectric() == null || req.closingWater() == null)) {
            throw bad("Chot chi so can du periodId, closingElectric, closingWater");
        }
        if (closing) {
            meterService.recordMoveOut(new MoveOutRequest(oldRoom.getRoomId(), req.periodId(),
                    req.closingElectric(), req.closingWater(), actorId, null, null));
        }
        List<?> residents = em.createQuery(
                "select r from RoomResident r where r.room.roomId = :room and r.isActive = true")
                .setParameter("room", oldRoom.getRoomId()).getResultList();
        for (Object o : residents) {
            ((com.group7.minihouse.shared.entity.RoomResident) o).setRoom(newRoom);
        }
        record(c, "ROOM_TRANSFER", oldRoom.getRoomCode(), newRoom.getRoomCode(), actorId);
        c.setRoom(newRoom);
        oldRoom.setStatus("VACANT");
        newRoom.setStatus("RENTED");
        audit(actorId, "TRANSFER_ROOM", "Chuyen hop dong " + c.getContractCode() + " tu phong "
                + oldRoom.getRoomCode() + " sang " + newRoom.getRoomCode(), ip);
        return toResponse(c);
    }

    // ------------------------------------------------------------------ UC51: cham dut

    @Transactional
    public ContractResponse terminate(Integer id, TerminateRequest req, Integer actorId, String ip) {
        RentalContract c = load(id);
        if (!OCCUPYING.contains(c.getStatus())) {
            throw conflict("Hop dong o trang thai " + c.getStatus() + ", khong con hieu luc de thao tac");
        }
        String reason = req == null || req.reason() == null ? "" : req.reason().trim();
        if (reason.length() < 10) {
            throw bad("Can ly do cham dut hop dong (it nhat 10 ky tu)");
        }
        if (reason.length() > 255) {
            throw bad("Ly do toi da 255 ky tu");
        }
        // Chấm dứt sớm là việc hiếm và không hoàn tác: phải gõ lại đúng mã phòng, và không được còn nợ.
        // Khách trả phòng bình thường thì dùng quy trình Trả phòng (kiểm tra phòng, quyết toán, hoàn cọc).
        String confirm = req == null || req.confirmRoomCode() == null ? "" : req.confirmRoomCode().trim();
        if (!confirm.equalsIgnoreCase(c.getRoom().getRoomCode())) {
            throw bad("Gõ lại đúng mã phòng " + c.getRoom().getRoomCode() + " để xác nhận chấm dứt");
        }
        Object owed = em.createQuery("select coalesce(sum(i.remainingAmount), 0) from Invoice i "
                        + "where i.calculatedFee.contract.contractId = :id and i.paymentStatus not in ('PAID', 'CANCELLED')")
                .setParameter("id", id).getSingleResult();
        if (owed instanceof java.math.BigDecimal debt && debt.signum() > 0) {
            throw bad("Hợp đồng còn nợ " + debt.toBigInteger() + " đồng. Thu đủ hoặc dùng mục Trả phòng để quyết toán với tiền cọc");
        }
        // Đang có yêu cầu trả phòng chưa xong thì phải xử lý nó (đường chuẩn), không chấm dứt chồng lên
        if (!em.createQuery("select r.checkoutId from CheckoutRequest r where r.contract.contractId = :id "
                        + "and r.status in ('PENDING', 'INSPECTING')").setParameter("id", id)
                .getResultList().isEmpty()) {
            throw conflict("Hop dong dang co yeu cau tra phong chua xu ly: hoan tat thanh ly o muc Tra phong hoac huy yeu cau do truoc");
        }
        Integer finalE = req.finalElectricMeter();
        Integer finalW = req.finalWaterMeter();
        if (finalE != null || finalW != null) {
            if (finalE == null || finalW == null || finalE < 0 || finalW < 0) {
                throw bad("Chi so dien va nuoc cuoi phai di cung nhau va khong am");
            }
            meterService.validateFinalReading(c.getRoom().getRoomId(), LocalDate.now(), finalE, finalW);
        }
        // Lịch sử TERMINATED, lịch sử ở, gỡ người ở, chốt chỉ số do ContractEndListener làm (cùng đường với Trả phòng)
        endService.end(c, LocalDate.now(), reason, actorId, finalE, finalW);
        audit(actorId, "TERMINATE_CONTRACT", "Cham dut hop dong " + c.getContractCode() + ": " + reason, ip);
        return toResponse(c);
    }

    // ------------------------------------------------------------------ noi bo

    private RentalContract load(Integer id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("Hop dong " + id));
    }

    /** Hop dong con hieu luc (ACTIVE/EXPIRING_SOON); het han hoac da cham dut thi khong sua duoc. */
    private RentalContract liveContract(Integer id) {
        RentalContract c = load(id);
        if (!LIVE.contains(c.getStatus())) {
            throw conflict("Hop dong o trang thai " + c.getStatus() + ", khong con hieu luc de thao tac");
        }
        return c;
    }

    private Room room(Integer id) {
        Room r = em.find(Room.class, id);
        if (r == null) {
            throw BusinessException.notFound("Phong " + id);
        }
        return r;
    }

    private Tenant tenant(Integer id) {
        return tenantRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Khach thue " + id));
    }

    private void record(RentalContract c, String type, String oldValue, String newValue, Integer actorId) {
        ContractAdjustment a = new ContractAdjustment();
        a.setContract(c);
        a.setAdjustmentType(type);
        a.setOldValue(limit(oldValue));
        a.setNewValue(limit(newValue));
        if (actorId != null) {
            a.setAdjustedBy(em.getReference(User.class, actorId));
        }
        em.persist(a);
    }

    private String newCode(Room room, LocalDate start) {
        String base = "HD" + start.format(CODE_DATE) + "-" + room.getRoomCode().replaceAll("[^A-Za-z0-9]", "");
        String code = base;
        int n = 1;
        while (repo.existsByContractCode(code)) {
            code = base + "-" + (++n);
        }
        return code;
    }

    private static void checkPricing(BigDecimal rent, BigDecimal deposit, int cycle, int dueDay) {
        if (rent == null || rent.signum() <= 0) {
            throw bad("Gia thue phai lon hon 0");
        }
        if (deposit == null || deposit.signum() < 0) {
            throw bad("Tien coc khong duoc am");
        }
        if (cycle < 1 || cycle > 12) {
            throw bad("Chu ky thanh toan tu 1 den 12 thang");
        }
        if (dueDay < 1 || dueDay > 28) {
            throw bad("Ngay han thanh toan tu 1 den 28");
        }
    }

    private void audit(Integer actorId, String action, String description, String ip) {
        if (actorId != null) {
            activityLogService.log(actorId, action, description, ip);
        }
    }

    private static String likePattern(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        if (k.isEmpty()) {
            return "%";
        }
        return "%" + k.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[") + "%";
    }

    private static String limit(String s) {
        return s == null ? null : s.length() > 255 ? s.substring(0, 255) : s;
    }

    private static String str(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String money(BigDecimal v) {
        return v == null ? "0" : String.format("%,.0f", v);
    }

    private static BusinessException bad(String m) {
        return new BusinessException(HttpStatus.BAD_REQUEST, m);
    }

    private static BusinessException conflict(String m) {
        return new BusinessException(HttpStatus.CONFLICT, m);
    }

    static ContractResponse toResponse(RentalContract c) {
        return new ContractResponse(c.getContractId(), c.getContractCode(), c.getRoom().getRoomId(),
                c.getRoom().getRoomCode(), c.getTenant().getTenantId(), c.getTenant().getFullName(),
                c.getTemplate() == null ? null : c.getTemplate().getTemplateId(), c.getStartDate(), c.getEndDate(),
                c.getMonthlyRentPrice(), c.getDepositAmount(), c.getPaymentCycleMonths(), c.getPaymentDueDay(),
                c.getLateFeePerDay(), c.getElectricTerms(), c.getWaterTerms(), c.getDepositRefundConditions(),
                c.getDigitalSignatureUrl(), c.getPdfContractUrl(), c.getStatus(), c.getCreatedAt());
    }

    static AdjustmentResponse toResponse(ContractAdjustment a) {
        return new AdjustmentResponse(a.getAdjustmentId(), a.getAdjustmentType(), a.getOldValue(), a.getNewValue(),
                a.getAdjustedBy() == null ? null : a.getAdjustedBy().getUserId(), a.getAdjustedAt());
    }
}
