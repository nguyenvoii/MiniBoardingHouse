package com.group7.minihouse.viet.service;

import static com.group7.minihouse.viet.service.VietSupport.nz;

import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.CheckoutInspection;
import com.group7.minihouse.shared.entity.CheckoutRequest;
import com.group7.minihouse.shared.entity.DepositRefundVoucher;
import com.group7.minihouse.phi.service.ContractEndService;
import com.group7.minihouse.shared.entity.ContractAdjustment;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.util.DepositLedger;
import com.group7.minihouse.toan.service.MeterReadingService;
import com.group7.minihouse.viet.dto.CheckoutDtos.CheckinRecord;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.CheckoutDtos.CheckoutResponse;
import com.group7.minihouse.viet.dto.CheckoutDtos.ConfirmScheduleRequest;
import com.group7.minihouse.viet.dto.CheckoutDtos.CreateCheckoutRequest;
import com.group7.minihouse.viet.dto.CheckoutDtos.DamageItem;
import com.group7.minihouse.viet.dto.CheckoutDtos.HandoverReport;
import com.group7.minihouse.viet.dto.CheckoutDtos.InspectionRequest;
import com.group7.minihouse.viet.dto.CheckoutDtos.InspectionResponse;
import com.group7.minihouse.viet.dto.CheckoutDtos.RefundVoucherResponse;
import com.group7.minihouse.viet.dto.CheckoutDtos.SettlementResponse;
import com.group7.minihouse.viet.dto.CheckoutDtos.UnpaidInvoiceItem;
import com.group7.minihouse.viet.repository.CheckoutInspectionRepository;
import com.group7.minihouse.viet.repository.CheckoutRequestRepository;
import com.group7.minihouse.viet.repository.DepositRefundVoucherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC101-107: tra phong. Luong: PENDING (tiep nhan) -> INSPECTING (xac nhan lich kiem tra)
 * -> ghi hien trang phong -> quyet toan -> lap phieu hoan coc -> hoan tien -> FINALIZED (thanh ly hop dong).
 * UC105: khi ghi nhan hoan tien (payRefund), phan coc da khau tru vao cong no duoc gach vao cac hoa don chua tra
 * (cu nhat truoc) bang cach tang paid_amount va doi payment_status; cong no (tenant_debts) duoc tinh lai ngay.
 */
@Service
public class CheckoutService {

    static final Set<String> STATUSES = Set.of("PENDING", "INSPECTING", "FINALIZED", "CANCELLED");
    private static final Set<String> OPEN = Set.of("PENDING", "INSPECTING");

    @PersistenceContext
    private EntityManager em;

    private final CheckoutRequestRepository checkoutRepository;
    private final CheckoutInspectionRepository inspectionRepository;
    private final DepositRefundVoucherRepository voucherRepository;
    private final TenantRoomResolver tenantRoomResolver;
    private final ActorResolver actorResolver;
    private final DebtService debtService;
    private final ContractEndService endService;
    private final MeterReadingService meterService;

    public CheckoutService(CheckoutRequestRepository checkoutRepository,
                           CheckoutInspectionRepository inspectionRepository,
                           DepositRefundVoucherRepository voucherRepository, TenantRoomResolver tenantRoomResolver,
                           ActorResolver actorResolver, DebtService debtService,
                           ContractEndService endService, MeterReadingService meterService) {
        this.meterService = meterService;
        this.checkoutRepository = checkoutRepository;
        this.inspectionRepository = inspectionRepository;
        this.voucherRepository = voucherRepository;
        this.tenantRoomResolver = tenantRoomResolver;
        this.actorResolver = actorResolver;
        this.debtService = debtService;
        this.endService = endService;
    }

    // ---------- UC101: tiep nhan yeu cau ----------

    /** tenantId != null: nguoi thue tu gui (chi duoc chon hop dong cua minh); null: nhan vien tao ho. */
    @Transactional
    public CheckoutResponse create(CreateCheckoutRequest req, Integer tenantId) {
        if (req == null || req.expectedCheckoutDate() == null) {
            throw new BusinessException("Thiếu ngày trả phòng dự kiến");
        }
        if (req.expectedCheckoutDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Ngày trả phòng dự kiến không được ở quá khứ");
        }
        RentalContract contract = resolveContract(req.contractId(), tenantId);
        // EXPIRED (het han nhung khach con o, chua thanh ly) van phai tra phong duoc
        if (!Set.of("ACTIVE", "EXPIRING_SOON", "EXPIRED").contains(contract.getStatus())) {
            throw new BusinessException("Hợp đồng không còn hiệu lực, không thể yêu cầu trả phòng");
        }
        if (checkoutRepository.existsByContract_ContractIdAndStatusIn(contract.getContractId(), OPEN)) {
            throw new BusinessException("Hợp đồng này đang có yêu cầu trả phòng chưa xử lý");
        }
        CheckoutRequest c = new CheckoutRequest();
        c.setContract(contract);
        c.setExpectedCheckoutDate(req.expectedCheckoutDate());
        c.setStatus("PENDING");
        c.setCreatedAt(LocalDateTime.now());
        return CheckoutResponse.from(checkoutRepository.save(c));
    }

    private RentalContract resolveContract(Integer contractId, Integer tenantId) {
        if (contractId == null) {
            if (tenantId == null) {
                throw new BusinessException("Thiếu contractId");
            }
            List<RentalContract> list = tenantRoomResolver.activeContracts(tenantId);
            if (list.size() != 1) {
                throw new BusinessException(list.isEmpty() ? "Khách thuê chưa có hợp đồng đang hiệu lực"
                        : "Khách thuê có nhiều hợp đồng, cần chọn contractId");
            }
            return list.get(0);
        }
        RentalContract contract = em.find(RentalContract.class, contractId);
        if (contract == null) {
            throw BusinessException.notFound("Hợp đồng");
        }
        if (tenantId != null && !contract.getTenant().getTenantId().equals(tenantId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Hợp đồng không thuộc về bạn");
        }
        return contract;
    }

    @Transactional(readOnly = true)
    public PageResponse<CheckoutResponse> search(String status, Integer contractId, int page, int size) {
        String st = VietSupport.enumOrNull(status, STATUSES, "status");
        return new PageResponse<>(checkoutRepository
                .search(st, contractId, VietSupport.page(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(CheckoutResponse::from));
    }

    @Transactional(readOnly = true)
    public List<CheckoutResponse> mine(Integer tenantId) {
        return checkoutRepository.findByContract_Tenant_TenantIdOrderByCreatedAtDesc(tenantId)
                .stream().map(CheckoutResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CheckoutResponse get(Integer id) {
        return CheckoutResponse.from(find(id));
    }

    /** UC101: xac nhan lich kiem tra phong -> INSPECTING. */
    @Transactional
    public CheckoutResponse confirmSchedule(Integer id, ConfirmScheduleRequest req) {
        CheckoutRequest c = find(id);
        requireStatus(c, "PENDING");
        if (req != null && req.expectedCheckoutDate() != null) {
            if (req.expectedCheckoutDate().isBefore(LocalDate.now())) {
                throw new BusinessException("Ngày kiểm tra không được ở quá khứ");
            }
            c.setExpectedCheckoutDate(req.expectedCheckoutDate());
        }
        c.setStatus("INSPECTING");
        return CheckoutResponse.from(checkoutRepository.save(c));
    }

    @Transactional
    public CheckoutResponse cancel(Integer id) {
        CheckoutRequest c = find(id);
        if (!OPEN.contains(c.getStatus())) {
            throw new BusinessException("Chỉ hủy được yêu cầu đang PENDING hoặc INSPECTING");
        }
        if (voucherRepository.findByCheckout_CheckoutId(id).isPresent()) {
            throw new BusinessException("Đã lập phiếu hoàn cọc, không thể hủy yêu cầu");
        }
        c.setStatus("CANCELLED");
        return CheckoutResponse.from(checkoutRepository.save(c));
    }

    // ---------- UC102, UC103: hien trang phong, boi thuong ----------

    @Transactional
    public InspectionResponse recordInspection(Integer id, InspectionRequest req, Integer actorId) {
        CheckoutRequest c = find(id);
        requireStatus(c, "INSPECTING");
        if (voucherRepository.findByCheckout_CheckoutId(id).isPresent()) {
            throw new BusinessException("Đã lập phiếu hoàn cọc, không thể sửa hiện trạng phòng");
        }
        if (req == null || req.finalElectricMeter() == null || req.finalWaterMeter() == null) {
            throw new BusinessException("Thiếu chỉ số điện/nước cuối");
        }
        if (req.finalElectricMeter() < 0 || req.finalWaterMeter() < 0) {
            throw new BusinessException("Chỉ số điện/nước không được âm");
        }
        // F1: so cuoi phai >= so ky truoc, bao loi ngay luc nhap (khong de den luc thanh ly moi hong)
        meterService.validateFinalReading(c.getContract().getRoom().getRoomId(), LocalDate.now(),
                req.finalElectricMeter(), req.finalWaterMeter());
        BigDecimal damageTotal = BigDecimal.ZERO;
        StringBuilder description = new StringBuilder();
        if (req.damages() != null) {
            for (DamageItem item : req.damages()) {
                String name = VietSupport.requireText(item == null ? null : item.name(), "ten thiet bi hu hong");
                if (item.cost() == null || item.cost().signum() < 0) {
                    throw new BusinessException("Chi phí bồi thường của \"" + name + "\" không hợp lệ");
                }
                damageTotal = damageTotal.add(item.cost());
                if (description.length() > 0) {
                    description.append("\n");
                }
                description.append(name).append(": ").append(item.cost().toPlainString());
            }
        }
        CheckoutInspection ins = inspectionRepository.findByCheckout_CheckoutId(id).orElseGet(CheckoutInspection::new);
        ins.setCheckout(c);
        ins.setFinalElectricMeter(req.finalElectricMeter());
        ins.setFinalWaterMeter(req.finalWaterMeter());
        ins.setDamagedEquipmentsDescription(description.length() == 0 ? null : description.toString());
        ins.setDamageCompensationAmount(damageTotal);
        ins.setUnpaidUtilitiesAmount(debtTotal(c.getContract().getContractId()));
        ins.setInspectionPhotoUrl(VietSupport.blankToNull(req.inspectionPhotoUrl()));
        ins.setInspectedBy(actorResolver.require(actorId));
        ins.setInspectedAt(LocalDateTime.now());
        CheckoutInspection saved = inspectionRepository.save(ins);
        // F6: ghi ngay so cuoi thanh so doc cua ky dang mo de vao hoa don thang cuoi (truoc khi quyet toan, hoan coc)
        boolean recorded = meterService.recordFinalReading(c.getContract().getRoom().getRoomId(), LocalDate.now(),
                req.finalElectricMeter(), req.finalWaterMeter(), saved.getInspectedBy().getUserId());
        return toResponse(saved, recorded);
    }

    @Transactional(readOnly = true)
    public InspectionResponse getInspection(Integer id) {
        find(id);
        return toResponse(inspectionRepository.findByCheckout_CheckoutId(id)
                .orElseThrow(() -> BusinessException.notFound("Biên bản kiểm tra phòng")));
    }

    private static InspectionResponse toResponse(CheckoutInspection i) {
        return toResponse(i, false);
    }

    /** meterRecorded: so cuoi da duoc ghi thanh so doc cua ky ghi so dang mo (false khi khong co ky mo). */
    private static InspectionResponse toResponse(CheckoutInspection i, boolean meterRecorded) {
        return new InspectionResponse(i.getInspectionId(), i.getCheckout().getCheckoutId(),
                i.getFinalElectricMeter(), i.getFinalWaterMeter(), i.getDamagedEquipmentsDescription(),
                i.getDamageCompensationAmount(), i.getUnpaidUtilitiesAmount(), i.getInspectionPhotoUrl(),
                i.getInspectedBy().getUserId(), i.getInspectedAt(), meterRecorded);
    }

    // ---------- UC104, UC105: quyet toan, khau tru coc ----------

    @Transactional(readOnly = true)
    public SettlementResponse settlement(Integer id) {
        CheckoutRequest c = find(id);
        List<Invoice> unpaid = unpaidInvoices(c.getContract().getContractId());
        BigDecimal debt = unpaid.stream().map(i -> nz(i.getRemainingAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal damage = inspectionRepository.findByCheckout_CheckoutId(id)
                .map(i -> nz(i.getDamageCompensationAmount())).orElse(BigDecimal.ZERO);
        // Chỉ hoàn phần cọc ĐÃ THU (phiếu thu cọc của hợp đồng), không phải số ghi trên hợp đồng
        BigDecimal contractDeposit = nz(c.getContract().getDepositAmount());
        BigDecimal depositCollected = DepositLedger.collected(em, c.getContract().getContractCode());
        BigDecimal deposit = depositCollected.min(contractDeposit);
        BigDecimal deductedDebt = debt.min(deposit);
        BigDecimal deductedDamage = damage.min(deposit.subtract(deductedDebt));
        BigDecimal refundable = deposit.subtract(deductedDebt).subtract(deductedDamage);
        // Da hoan tien: hoa don da duoc gach no bang coc (UC105), nen so lieu chot lay tu phieu hoan coc,
        // phan no con lai (neu coc khong du) van la cac hoa don chua tra.
        DepositRefundVoucher refunded = voucherRepository.findByCheckout_CheckoutId(id)
                .filter(v -> "REFUNDED".equals(v.getStatus())).orElse(null);
        if (refunded != null) {
            deposit = nz(refunded.getOriginalDepositAmount());
            deductedDebt = nz(refunded.getDeductedDebtAmount());
            deductedDamage = nz(refunded.getDeductedDamageAmount());
            refundable = nz(refunded.getActualRefundAmount());
            debt = deductedDebt.add(debt);
        }
        BigDecimal mustPay = debt.subtract(deductedDebt).add(damage.subtract(deductedDamage));
        List<UnpaidInvoiceItem> items = unpaid.stream().map(i -> new UnpaidInvoiceItem(i.getInvoiceId(),
                i.getInvoiceCode(), i.getDueDate(), i.getRemainingAmount())).toList();
        return new SettlementResponse(id, deposit, debt, damage, deductedDebt, deductedDamage, refundable, mustPay,
                items, contractDeposit, depositCollected, finalMonthBilled(c));
    }

    /** Đã có hóa đơn cho tháng trả phòng chưa (tiền phòng theo ngày và điện nước phần tháng cuối). */
    private boolean finalMonthBilled(CheckoutRequest c) {
        java.time.LocalDate d = c.getActualCheckoutDate() != null ? c.getActualCheckoutDate() : c.getExpectedCheckoutDate();
        String ym = d.getYear() + "-" + String.format("%02d", d.getMonthValue());
        Long n = (Long) em.createQuery("select count(i) from Invoice i where i.calculatedFee.contract.contractId = :c "
                        + "and i.calculatedFee.billingPeriod.monthYear = :ym and i.paymentStatus <> 'CANCELLED'")
                .setParameter("c", c.getContract().getContractId()).setParameter("ym", ym).getSingleResult();
        return n > 0;
    }

    private List<Invoice> unpaidInvoices(Integer contractId) {
        return em.createQuery("select i from Invoice i join fetch i.calculatedFee f where f.contract.contractId = :c"
                + " and i.paymentStatus in ('UNPAID','PARTIALLY_PAID','OVERDUE') and i.remainingAmount > 0"
                + " order by i.dueDate, i.invoiceId", Invoice.class).setParameter("c", contractId).getResultList();
    }

    private BigDecimal debtTotal(Integer contractId) {
        return unpaidInvoices(contractId).stream().map(i -> nz(i.getRemainingAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- UC106: phieu hoan coc ----------

    @Transactional
    public RefundVoucherResponse createRefundVoucher(Integer id) {
        CheckoutRequest c = find(id);
        requireStatus(c, "INSPECTING");
        if (inspectionRepository.findByCheckout_CheckoutId(id).isEmpty()) {
            throw new BusinessException("Cần ghi nhận hiện trạng phòng trước khi lập phiếu hoàn cọc");
        }
        if (voucherRepository.findByCheckout_CheckoutId(id).isPresent()) {
            throw new BusinessException("Yêu cầu trả phòng này đã có phiếu hoàn cọc");
        }
        SettlementResponse s = settlement(id);
        DepositRefundVoucher v = new DepositRefundVoucher();
        v.setCheckout(c);
        v.setOriginalDepositAmount(s.originalDeposit());
        v.setDeductedDebtAmount(s.deductedDebt());
        v.setDeductedDamageAmount(s.deductedDamage());
        v.setActualRefundAmount(s.refundable());
        v.setStatus("PENDING");
        return RefundVoucherResponse.from(voucherRepository.save(v));
    }

    @Transactional(readOnly = true)
    public RefundVoucherResponse getRefundVoucher(Integer checkoutId) {
        find(checkoutId);
        return RefundVoucherResponse.from(voucherRepository.findByCheckout_CheckoutId(checkoutId)
                .orElseThrow(() -> BusinessException.notFound("Phiếu hoàn cọc")));
    }

    /** M3: huy phieu hoan coc con PENDING de lap lai (hoac sua hien trang phong, huy yeu cau). Da hoan tien thi khong huy duoc. */
    @Transactional
    public void cancelRefundVoucher(Integer checkoutId) {
        find(checkoutId);
        DepositRefundVoucher v = voucherRepository.findByCheckout_CheckoutId(checkoutId)
                .orElseThrow(() -> BusinessException.notFound("Phiếu hoàn cọc"));
        if (!"PENDING".equals(v.getStatus())) {
            throw new BusinessException("Phiếu hoàn cọc đã được hoàn tiền, không hủy được");
        }
        voucherRepository.delete(v);
    }

    /** Ghi nhan da chi tra tien hoan coc. Phieu duoc tinh lai theo so lieu hien tai (M3). */
    @Transactional
    public RefundVoucherResponse payRefund(Integer refundId) {
        DepositRefundVoucher v = voucherRepository.findById(refundId)
                .orElseThrow(() -> BusinessException.notFound("Phiếu hoàn cọc"));
        if (!"PENDING".equals(v.getStatus())) {
            throw new BusinessException("Phiếu hoàn cọc này đã được hoàn tiền");
        }
        // M3: phieu la anh chup luc lap; khach co the da tra no / co hoa don moi phat hanh tu do. Tinh lai theo so
        // lieu hien tai ngay luc hoan (truoc khi doi trang thai) de khong tru coc cho khoan da tra hai lan.
        SettlementResponse fresh = settlement(v.getCheckout().getCheckoutId());
        v.setOriginalDepositAmount(fresh.originalDeposit());
        v.setDeductedDebtAmount(fresh.deductedDebt());
        v.setDeductedDamageAmount(fresh.deductedDamage());
        v.setActualRefundAmount(fresh.refundable());
        v.setStatus("REFUNDED");
        v.setRefundDate(LocalDateTime.now());
        DepositRefundVoucher saved = voucherRepository.save(v);
        applyDepositToInvoices(saved.getCheckout().getContract().getContractId(), nz(saved.getDeductedDebtAmount()));
        em.flush();
        debtService.recalculate();
        return RefundVoucherResponse.from(saved);
    }

    /**
     * UC105: gach no hoa don bang tien coc da khau tru. Di tu hoa don cu nhat, moi hoa don gach toi da
     * so con no cua no; het tien thi dung. Hoa don duoc gach du -> PAID, gach mot phan -> PARTIALLY_PAID.
     */
    private void applyDepositToInvoices(Integer contractId, BigDecimal amount) {
        BigDecimal left = amount;
        for (Invoice i : unpaidInvoices(contractId)) {
            if (left.signum() <= 0) {
                break;
            }
            em.refresh(i, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); // khoa + doc lai so da tra moi nhat
            BigDecimal remaining = nz(i.getTotalAmount()).subtract(nz(i.getPaidAmount()));
            if ("CANCELLED".equals(i.getPaymentStatus()) || remaining.signum() <= 0) {
                continue;
            }
            BigDecimal pay = remaining.min(left);
            // dung chung quy tac trang thai cua InvoiceBalanceCalculator (PAID / OVERDUE / PARTIALLY_PAID)
            new com.group7.minihouse.toan.service.InvoiceBalanceCalculator()
                    .setPaid(i, nz(i.getPaidAmount()).add(pay), LocalDate.now());
            left = left.subtract(pay);
        }
    }

    // ---------- UC107: ban giao, thanh ly ----------

    @Transactional
    public CheckoutResponse finalizeCheckout(Integer id, Integer actorId) {
        CheckoutRequest c = find(id);
        requireStatus(c, "INSPECTING");
        DepositRefundVoucher v = voucherRepository.findByCheckout_CheckoutId(id)
                .orElseThrow(() -> new BusinessException("Chưa lập phiếu hoàn cọc"));
        if (!"REFUNDED".equals(v.getStatus())) {
            throw new BusinessException("Cần ghi nhận hoàn tiền đặt cọc trước khi thanh lý hợp đồng");
        }
        c.setStatus("FINALIZED");
        c.setActualCheckoutDate(LocalDate.now());
        RentalContract contract = c.getContract();
        CheckoutRequest saved = checkoutRepository.save(c);
        var ins = inspectionRepository.findByCheckout_CheckoutId(id).orElse(null);
        // F8: khach con phai tra them (no / boi thuong vuot coc) thi ghi ro vao lich su hop dong, khong de bien mat
        SettlementResponse s = settlement(id);
        if (s.tenantMustPay().signum() > 0) {
            ContractAdjustment a = new ContractAdjustment();
            a.setContract(contract);
            a.setAdjustmentType("DEPOSIT_SHORTFALL");
            a.setOldValue("Coc hoan " + nz(s.refundable()).toBigInteger());
            a.setNewValue("Khách còn phải trả " + s.tenantMustPay().toBigInteger() + " dong (vuot coc)");
            if (actorId != null) {
                a.setAdjustedBy(em.getReference(User.class, actorId));
            }
            em.persist(a);
        }
        // Dung chung duong ket thuc hop dong voi Cham dut som (ContractEndService)
        endService.end(contract, saved.getActualCheckoutDate(), "Trả phòng, thanh lý hợp đồng",
                actorId != null ? actorId
                        : ins == null || ins.getInspectedBy() == null ? null : ins.getInspectedBy().getUserId(),
                ins == null ? null : ins.getFinalElectricMeter(), ins == null ? null : ins.getFinalWaterMeter());
        return CheckoutResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public HandoverReport handoverReport(Integer id) {
        CheckoutRequest c = find(id);
        InspectionResponse ins = inspectionRepository.findByCheckout_CheckoutId(id).map(CheckoutService::toResponse)
                .orElse(null);
        RefundVoucherResponse voucher = voucherRepository.findByCheckout_CheckoutId(id)
                .map(RefundVoucherResponse::from).orElse(null);
        return new HandoverReport(CheckoutResponse.from(c), ins, settlement(id), voucher,
                checkinRecord(c.getContract().getContractId()));
    }

    /** F5: bien ban nhan phong cua hop dong (null neu chua lap) de doi chieu hien trang luc tra phong. */
    @Transactional(readOnly = true)
    public CheckinRecord checkinRecord(Integer contractId) {
        return em.createQuery("select k from CheckinInspection k where k.contract.contractId = :c order by k.inspectionId desc",
                        com.group7.minihouse.chi.entity.CheckinInspection.class)
                .setParameter("c", contractId).setMaxResults(1).getResultList().stream().findFirst()
                .map(k -> new CheckinRecord(k.getInspectionId(), k.getCheckinDate(), k.getInitialElectricMeter(),
                        k.getInitialWaterMeter(), k.getKeyHandedCount(), k.getFurnishingsCondition(), k.getNotes(),
                        k.getInspectionPhotoUrl(), k.getTenantConfirmed(), k.getInspectedByName()))
                .orElse(null);
    }

    private CheckoutRequest find(Integer id) {
        return checkoutRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Yêu cầu trả phòng"));
    }

    private static void requireStatus(CheckoutRequest c, String expected) {
        if (!expected.equals(c.getStatus())) {
            throw new BusinessException("Yêu cầu trả phòng phải ở trạng thái " + expected
                    + " (hiện tại: " + c.getStatus() + ")");
        }
    }
}
