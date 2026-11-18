package com.group7.minihouse.viet.service;

import static com.group7.minihouse.viet.service.VietSupport.nz;

import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.CheckoutInspection;
import com.group7.minihouse.shared.entity.CheckoutRequest;
import com.group7.minihouse.shared.entity.DepositRefundVoucher;
import com.group7.minihouse.shared.event.ContractEndedEvent;
import com.group7.minihouse.shared.util.DepositLedger;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    public CheckoutService(CheckoutRequestRepository checkoutRepository,
                           CheckoutInspectionRepository inspectionRepository,
                           DepositRefundVoucherRepository voucherRepository, TenantRoomResolver tenantRoomResolver,
                           ActorResolver actorResolver, DebtService debtService,
                           ApplicationEventPublisher events) {
        this.checkoutRepository = checkoutRepository;
        this.inspectionRepository = inspectionRepository;
        this.voucherRepository = voucherRepository;
        this.tenantRoomResolver = tenantRoomResolver;
        this.actorResolver = actorResolver;
        this.debtService = debtService;
        this.events = events;
    }

    // ---------- UC101: tiep nhan yeu cau ----------

    /** tenantId != null: nguoi thue tu gui (chi duoc chon hop dong cua minh); null: nhan vien tao ho. */
    @Transactional
    public CheckoutResponse create(CreateCheckoutRequest req, Integer tenantId) {
        if (req == null || req.expectedCheckoutDate() == null) {
            throw new BusinessException("Thieu ngay tra phong du kien");
        }
        if (req.expectedCheckoutDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Ngay tra phong du kien khong duoc o qua khu");
        }
        RentalContract contract = resolveContract(req.contractId(), tenantId);
        if (!Set.of("ACTIVE", "EXPIRING_SOON").contains(contract.getStatus())) {
            throw new BusinessException("Hop dong khong con hieu luc, khong the yeu cau tra phong");
        }
        if (checkoutRepository.existsByContract_ContractIdAndStatusIn(contract.getContractId(), OPEN)) {
            throw new BusinessException("Hop dong nay dang co yeu cau tra phong chua xu ly");
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
                throw new BusinessException("Thieu contractId");
            }
            List<RentalContract> list = tenantRoomResolver.activeContracts(tenantId);
            if (list.size() != 1) {
                throw new BusinessException(list.isEmpty() ? "Khach thue chua co hop dong dang hieu luc"
                        : "Khach thue co nhieu hop dong, can chon contractId");
            }
            return list.get(0);
        }
        RentalContract contract = em.find(RentalContract.class, contractId);
        if (contract == null) {
            throw BusinessException.notFound("Hop dong");
        }
        if (tenantId != null && !contract.getTenant().getTenantId().equals(tenantId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Hop dong khong thuoc ve ban");
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
                throw new BusinessException("Ngay kiem tra khong duoc o qua khu");
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
            throw new BusinessException("Chi huy duoc yeu cau dang PENDING hoac INSPECTING");
        }
        if (voucherRepository.findByCheckout_CheckoutId(id).isPresent()) {
            throw new BusinessException("Da lap phieu hoan coc, khong the huy yeu cau");
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
            throw new BusinessException("Da lap phieu hoan coc, khong the sua hien trang phong");
        }
        if (req == null || req.finalElectricMeter() == null || req.finalWaterMeter() == null) {
            throw new BusinessException("Thieu chi so dien/nuoc cuoi");
        }
        if (req.finalElectricMeter() < 0 || req.finalWaterMeter() < 0) {
            throw new BusinessException("Chi so dien/nuoc khong duoc am");
        }
        BigDecimal damageTotal = BigDecimal.ZERO;
        StringBuilder description = new StringBuilder();
        if (req.damages() != null) {
            for (DamageItem item : req.damages()) {
                String name = VietSupport.requireText(item == null ? null : item.name(), "ten thiet bi hu hong");
                if (item.cost() == null || item.cost().signum() < 0) {
                    throw new BusinessException("Chi phi boi thuong cua \"" + name + "\" khong hop le");
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
        return toResponse(inspectionRepository.save(ins));
    }

    @Transactional(readOnly = true)
    public InspectionResponse getInspection(Integer id) {
        find(id);
        return toResponse(inspectionRepository.findByCheckout_CheckoutId(id)
                .orElseThrow(() -> BusinessException.notFound("Bien ban kiem tra phong")));
    }

    private static InspectionResponse toResponse(CheckoutInspection i) {
        return new InspectionResponse(i.getInspectionId(), i.getCheckout().getCheckoutId(),
                i.getFinalElectricMeter(), i.getFinalWaterMeter(), i.getDamagedEquipmentsDescription(),
                i.getDamageCompensationAmount(), i.getUnpaidUtilitiesAmount(), i.getInspectionPhotoUrl(),
                i.getInspectedBy().getUserId(), i.getInspectedAt());
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
            throw new BusinessException("Can ghi nhan hien trang phong truoc khi lap phieu hoan coc");
        }
        if (voucherRepository.findByCheckout_CheckoutId(id).isPresent()) {
            throw new BusinessException("Yeu cau tra phong nay da co phieu hoan coc");
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
                .orElseThrow(() -> BusinessException.notFound("Phieu hoan coc")));
    }

    /** Ghi nhan da chi tra tien hoan coc. */
    @Transactional
    public RefundVoucherResponse payRefund(Integer refundId) {
        DepositRefundVoucher v = voucherRepository.findById(refundId)
                .orElseThrow(() -> BusinessException.notFound("Phieu hoan coc"));
        if (!"PENDING".equals(v.getStatus())) {
            throw new BusinessException("Phieu hoan coc nay da duoc hoan tien");
        }
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
            BigDecimal remaining = nz(i.getRemainingAmount());
            BigDecimal pay = remaining.min(left);
            i.setPaidAmount(nz(i.getPaidAmount()).add(pay));
            i.setPaymentStatus(pay.compareTo(remaining) >= 0 ? "PAID" : "PARTIALLY_PAID");
            left = left.subtract(pay);
        }
    }

    // ---------- UC107: ban giao, thanh ly ----------

    @Transactional
    public CheckoutResponse finalizeCheckout(Integer id, Integer actorId) {
        CheckoutRequest c = find(id);
        requireStatus(c, "INSPECTING");
        DepositRefundVoucher v = voucherRepository.findByCheckout_CheckoutId(id)
                .orElseThrow(() -> new BusinessException("Chua lap phieu hoan coc"));
        if (!"REFUNDED".equals(v.getStatus())) {
            throw new BusinessException("Can ghi nhan hoan tien dat coc truoc khi thanh ly hop dong");
        }
        c.setStatus("FINALIZED");
        c.setActualCheckoutDate(LocalDate.now());
        RentalContract contract = c.getContract();
        contract.setStatus("TERMINATED");
        contract.getRoom().setStatus("VACANT");
        em.merge(contract);
        em.merge(contract.getRoom());
        CheckoutRequest saved = checkoutRepository.save(c);
        var ins = inspectionRepository.findByCheckout_CheckoutId(id).orElse(null);
        events.publishEvent(new ContractEndedEvent(contract.getContractId(), contract.getRoom().getRoomId(),
                contract.getRoom().getProperty() == null ? null : contract.getRoom().getProperty().getPropertyId(),
                saved.getActualCheckoutDate(), ins == null ? null : ins.getFinalElectricMeter(),
                ins == null ? null : ins.getFinalWaterMeter(), "Tra phong, thanh ly hop dong",
                actorId != null ? actorId : ins == null || ins.getInspectedBy() == null ? null : ins.getInspectedBy().getUserId()));
        return CheckoutResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public HandoverReport handoverReport(Integer id) {
        CheckoutRequest c = find(id);
        InspectionResponse ins = inspectionRepository.findByCheckout_CheckoutId(id).map(CheckoutService::toResponse)
                .orElse(null);
        RefundVoucherResponse voucher = voucherRepository.findByCheckout_CheckoutId(id)
                .map(RefundVoucherResponse::from).orElse(null);
        return new HandoverReport(CheckoutResponse.from(c), ins, settlement(id), voucher);
    }

    private CheckoutRequest find(Integer id) {
        return checkoutRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Yeu cau tra phong"));
    }

    private static void requireStatus(CheckoutRequest c, String expected) {
        if (!expected.equals(c.getStatus())) {
            throw new BusinessException("Yeu cau tra phong phai o trang thai " + expected
                    + " (hien tai: " + c.getStatus() + ")");
        }
    }
}
