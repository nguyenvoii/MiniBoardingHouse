package com.group7.minihouse.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.group7.minihouse.dto.MeterDtos.*;
import com.group7.minihouse.entity.MeterAdjustmentLog;
import com.group7.minihouse.entity.MeterAnomalyAlert;
import com.group7.minihouse.entity.MeterPhoto;
import com.group7.minihouse.entity.MeterReading;
import com.group7.minihouse.entity.Property;
import com.group7.minihouse.entity.Room;
import com.group7.minihouse.entity.User;
import com.group7.minihouse.entity.UtilityReadingPeriod;
import com.group7.minihouse.repository.MeterAdjustmentLogRepository;
import com.group7.minihouse.repository.MeterAnomalyAlertRepository;
import com.group7.minihouse.repository.MeterPhotoRepository;
import com.group7.minihouse.repository.MeterReadingRepository;
import com.group7.minihouse.repository.UtilityReadingPeriodRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Chi so dien nuoc (UC 53-60): ky ghi so, nhap/chot chi so, anh cong to, dieu chinh,
 * canh bao tang dot bien, lich su va so sanh tieu thu, chi so luc vao/ra.
 */
@Service
public class MeterReadingService {

    /** Tang tu muc nay (%) so voi ky truoc thi canh bao. */
    public static final BigDecimal ANOMALY_THRESHOLD_PERCENT = new BigDecimal("50");
    /** Ky truoc phai tieu thu it nhat muc nay moi so sanh (tranh chia cho 0 / nhieu du lieu be). */
    private static final int MIN_BASE_USAGE = 1;

    private final UtilityReadingPeriodRepository periodRepo;
    private final MeterReadingRepository readingRepo;
    private final MeterPhotoRepository photoRepo;
    private final MeterAdjustmentLogRepository adjustmentRepo;
    private final MeterAnomalyAlertRepository alertRepo;

    @PersistenceContext
    private EntityManager em;

    public MeterReadingService(UtilityReadingPeriodRepository periodRepo, MeterReadingRepository readingRepo,
                               MeterPhotoRepository photoRepo, MeterAdjustmentLogRepository adjustmentRepo,
                               MeterAnomalyAlertRepository alertRepo) {
        this.periodRepo = periodRepo;
        this.readingRepo = readingRepo;
        this.photoRepo = photoRepo;
        this.adjustmentRepo = adjustmentRepo;
        this.alertRepo = alertRepo;
    }

    // ------------------------------------------------------------------ UC 53: ky ghi so

    @Transactional
    public PeriodResponse createPeriod(PeriodRequest req) {
        if (req.propertyId() == null || req.periodName() == null || req.periodName().isBlank()
                || req.startDate() == null || req.endDate() == null) {
            throw bad("Can propertyId, periodName, startDate, endDate");
        }
        if (req.endDate().isBefore(req.startDate())) {
            throw bad("Ngay ket thuc ky phai sau ngay bat dau");
        }
        if (periodRepo.existsByPropertyPropertyIdAndPeriodName(req.propertyId(), req.periodName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ky ghi so nay da ton tai");
        }
        UtilityReadingPeriod p = new UtilityReadingPeriod();
        p.setProperty(ref(Property.class, req.propertyId()));
        p.setPeriodName(req.periodName().trim());
        p.setStartDate(req.startDate());
        p.setEndDate(req.endDate());
        p.setIsClosed(false);
        return toResponse(periodRepo.save(p));
    }

    @Transactional(readOnly = true)
    public List<PeriodResponse> listPeriods(Integer propertyId) {
        return periodRepo.findByPropertyPropertyIdOrderByStartDateDesc(propertyId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public PeriodResponse closePeriod(Integer periodId, boolean closed) {
        UtilityReadingPeriod p = findPeriod(periodId);
        p.setIsClosed(closed);
        return toResponse(p);
    }

    /** Job: tu dong chot (dong) cac ky ghi so da qua ngay ket thuc hon graceDays ngay. */
    @Transactional
    public int autoCloseExpiredPeriods(int graceDays, LocalDate today) {
        var expired = periodRepo.findByIsClosedAndEndDateBefore(false, today.minusDays(graceDays));
        expired.forEach(p -> p.setIsClosed(true));
        return expired.size();
    }

    // ------------------------------------------------------------------ UC 54, 55: nhap chi so + anh

    @Transactional
    public ReadingResponse recordReading(ReadingRequest req) {
        if (req.periodId() == null || req.roomId() == null || req.newElectric() == null
                || req.newWater() == null || req.recordedByUserId() == null) {
            throw bad("Can periodId, roomId, newElectric, newWater, recordedByUserId");
        }
        UtilityReadingPeriod period = findPeriod(req.periodId());
        if (Boolean.TRUE.equals(period.getIsClosed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ky ghi so da dong");
        }
        Room room = em.find(Room.class, req.roomId());
        if (room == null) {
            throw notFound("Phong " + req.roomId());
        }
        if (room.getProperty() != null && !room.getProperty().getPropertyId()
                .equals(period.getProperty().getPropertyId())) {
            throw bad("Phong khong thuoc khu cua ky ghi so");
        }
        if (readingRepo.findByPeriodPeriodIdAndRoomRoomId(req.periodId(), req.roomId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phong nay da co chi so trong ky");
        }

        int[] base = resolveOldValues(room, period, req.oldElectric(), req.oldWater());
        if (req.newElectric() < base[0]) {
            throw bad("Chi so dien moi (" + req.newElectric() + ") nho hon chi so cu (" + base[0] + ")");
        }
        if (req.newWater() < base[1]) {
            throw bad("Chi so nuoc moi (" + req.newWater() + ") nho hon chi so cu (" + base[1] + ")");
        }

        MeterReading r = new MeterReading();
        r.setPeriod(period);
        r.setRoom(room);
        r.setOldElectric(base[0]);
        r.setNewElectric(req.newElectric());
        r.setOldWater(base[1]);
        r.setNewWater(req.newWater());
        r.setReadingDate(LocalDateTime.now());
        r.setRecordedByUser(ref(User.class, req.recordedByUserId()));
        r.setIsVerified(false);
        r = readingRepo.save(r);
        readingRepo.flush();
        em.refresh(r); // lay used_electric / used_water (cot tinh toan trong DB)

        addPhotoIfPresent(r, "ELECTRIC", req.electricPhotoUrl());
        addPhotoIfPresent(r, "WATER", req.waterPhotoUrl());
        detectAnomalies(r);
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public List<ReadingResponse> listReadings(Integer periodId) {
        return readingRepo.findByPeriodPeriodIdOrderByRoomRoomCode(periodId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReadingResponse getReading(Integer readingId) {
        return toResponse(findReading(readingId));
    }

    @Transactional
    public PhotoResponse addPhoto(Integer readingId, PhotoRequest req) {
        MeterReading r = findReading(readingId);
        if (req.photoUrl() == null || req.photoUrl().isBlank()) {
            throw bad("Thieu photoUrl");
        }
        String type = req.meterType() == null ? "" : req.meterType().toUpperCase();
        if (!type.equals("ELECTRIC") && !type.equals("WATER")) {
            throw bad("meterType phai la ELECTRIC hoac WATER");
        }
        return toResponse(savePhoto(r, type, req.photoUrl().trim()));
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> listPhotos(Integer readingId) {
        return photoRepo.findByReadingReadingId(readingId).stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------ UC 56: xac nhan & dieu chinh

    @Transactional
    public ReadingResponse confirmReading(Integer readingId) {
        MeterReading r = findReading(readingId);
        r.setIsVerified(true);
        return toResponse(r);
    }

    @Transactional
    public ReadingResponse adjustReading(Integer readingId, AdjustRequest req) {
        MeterReading r = findReading(readingId);
        if (Boolean.TRUE.equals(r.getPeriod().getIsClosed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ky ghi so da dong, khong the dieu chinh");
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw bad("Phai nhap ly do dieu chinh");
        }
        if (req.newValue() == null || req.adjustedByUserId() == null) {
            throw bad("Can newValue va adjustedByUserId");
        }
        String field = req.adjustedField() == null ? "" : req.adjustedField().toUpperCase();
        int oldVal;
        switch (field) {
            case "NEW_ELECTRIC" -> {
                if (req.newValue() < r.getOldElectric()) {
                    throw bad("Chi so dien moi nho hon chi so cu");
                }
                oldVal = r.getNewElectric();
                r.setNewElectric(req.newValue());
            }
            case "NEW_WATER" -> {
                if (req.newValue() < r.getOldWater()) {
                    throw bad("Chi so nuoc moi nho hon chi so cu");
                }
                oldVal = r.getNewWater();
                r.setNewWater(req.newValue());
            }
            default -> throw bad("adjustedField phai la NEW_ELECTRIC hoac NEW_WATER");
        }
        MeterAdjustmentLog log = new MeterAdjustmentLog();
        log.setReading(r);
        log.setAdjustedField(field);
        log.setOldVal(oldVal);
        log.setNewVal(req.newValue());
        log.setReason(req.reason().trim());
        log.setAdjustedBy(ref(User.class, req.adjustedByUserId()));
        log.setAdjustedAt(LocalDateTime.now());
        adjustmentRepo.save(log);

        r.setIsVerified(true);
        readingRepo.flush();
        em.refresh(r);
        // tinh lai canh bao chua xu ly cua ban ghi nay
        alertRepo.deleteByReadingReadingIdAndIsResolved(r.getReadingId(), false);
        alertRepo.flush();
        detectAnomalies(r);
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public List<AdjustmentLogResponse> listAdjustments(Integer readingId) {
        return adjustmentRepo.findByReadingReadingIdOrderByAdjustedAtDesc(readingId).stream()
                .map(l -> new AdjustmentLogResponse(l.getAdjId(), readingId, l.getAdjustedField(),
                        l.getOldVal(), l.getNewVal(), l.getReason(),
                        l.getAdjustedBy().getUserId(), l.getAdjustedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ UC 57: canh bao tang dot bien

    @Transactional(readOnly = true)
    public List<AlertResponse> searchAlerts(Integer propertyId, Boolean resolved, Integer roomId) {
        return new DynamicQuery("select a from MeterAnomalyAlert a where 1 = 1")
                .always("a.reading.period.property.propertyId = :pid", "pid", propertyId)
                .and("a.isResolved = :resolved", "resolved", resolved)
                .and("a.reading.room.roomId = :rid", "rid", roomId)
                .orderBy("a.alertId desc")
                .list(em, MeterAnomalyAlert.class).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AlertResponse resolveAlert(Integer alertId) {
        MeterAnomalyAlert a = alertRepo.findById(alertId)
                .orElseThrow(() -> notFound("Canh bao " + alertId));
        a.setIsResolved(true);
        return toResponse(a);
    }

    /** So sanh muc tieu thu voi ky truoc va tao canh bao neu tang vuot nguong. */
    void detectAnomalies(MeterReading r) {
        var prev = readingRepo.findFirstByRoomRoomIdAndPeriodEndDateLessThanOrderByPeriodEndDateDesc(
                r.getRoom().getRoomId(), r.getPeriod().getStartDate());
        if (prev.isEmpty()) {
            return;
        }
        MeterReading p = prev.get();
        checkUsage(r, "ELECTRIC", "dien", usedOf(p.getNewElectric(), p.getOldElectric()),
                usedOf(r.getNewElectric(), r.getOldElectric()));
        checkUsage(r, "WATER", "nuoc", usedOf(p.getNewWater(), p.getOldWater()),
                usedOf(r.getNewWater(), r.getOldWater()));
    }

    private void checkUsage(MeterReading r, String type, String label, int prevUsed, int curUsed) {
        if (prevUsed < MIN_BASE_USAGE || curUsed <= prevUsed) {
            return;
        }
        BigDecimal pct = BigDecimal.valueOf(curUsed - prevUsed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(prevUsed), 2, RoundingMode.HALF_UP);
        if (pct.compareTo(ANOMALY_THRESHOLD_PERCENT) < 0) {
            return;
        }
        MeterAnomalyAlert a = new MeterAnomalyAlert();
        a.setReading(r);
        a.setUtilityType(type);
        // cot DECIMAL(5,2) -> toi da 999.99
        a.setIncreasePercentage(pct.min(new BigDecimal("999.99")));
        a.setAlertMessage("Phong " + r.getRoom().getRoomCode() + ": " + label + " tang " + pct
                + "% so voi ky truoc (" + prevUsed + " -> " + curUsed + ")");
        a.setIsResolved(false);
        alertRepo.save(a);
    }

    // ------------------------------------------------------------------ UC 58, 59: lich su & so sanh

    @Transactional(readOnly = true)
    public ConsumptionHistory roomHistory(Integer roomId, LocalDate from, LocalDate to) {
        Room room = em.find(Room.class, roomId);
        if (room == null) {
            throw notFound("Phong " + roomId);
        }
        List<ConsumptionPoint> points = readingRepo.findHistoryByRoom(roomId, orMin(from), orMax(to)).stream()
                .map(r -> new ConsumptionPoint(r.getPeriod().getPeriodId(), r.getPeriod().getPeriodName(),
                        r.getPeriod().getStartDate(),
                        usedOf(r.getNewElectric(), r.getOldElectric()),
                        usedOf(r.getNewWater(), r.getOldWater())))
                .toList();
        return new ConsumptionHistory(roomId, room.getRoomCode(), points);
    }

    /** Tong tieu thu toan khu theo tung ky - du lieu ve bieu do so sanh cac thang. */
    @Transactional(readOnly = true)
    public List<ConsumptionPoint> propertyComparison(Integer propertyId, LocalDate from, LocalDate to) {
        var byPeriod = new java.util.LinkedHashMap<Integer, long[]>();
        var meta = new java.util.LinkedHashMap<Integer, UtilityReadingPeriod>();
        for (MeterReading r : readingRepo.findHistoryByProperty(propertyId, orMin(from), orMax(to))) {
            UtilityReadingPeriod p = r.getPeriod();
            meta.putIfAbsent(p.getPeriodId(), p);
            long[] sum = byPeriod.computeIfAbsent(p.getPeriodId(), k -> new long[2]);
            sum[0] += usedOf(r.getNewElectric(), r.getOldElectric());
            sum[1] += usedOf(r.getNewWater(), r.getOldWater());
        }
        List<ConsumptionPoint> out = new ArrayList<>();
        byPeriod.forEach((id, sum) -> {
            UtilityReadingPeriod p = meta.get(id);
            out.add(new ConsumptionPoint(id, p.getPeriodName(), p.getStartDate(), sum[0], sum[1]));
        });
        return out;
    }

    // ------------------------------------------------------------------ UC 60: chi so luc vao / ra

    /** Ghi chi so ban dau khi khach bat dau thue (cap nhat chi so khoi dau cua phong). */
    @Transactional
    public MoveInResponse recordMoveIn(MoveInRequest req) {
        if (req.roomId() == null || req.electric() == null || req.water() == null
                || req.electric() < 0 || req.water() < 0) {
            throw bad("Can roomId, electric >= 0, water >= 0");
        }
        Room room = em.find(Room.class, req.roomId());
        if (room == null) {
            throw notFound("Phong " + req.roomId());
        }
        room.setInitialElectricMeter(req.electric());
        room.setInitialWaterMeter(req.water());
        return new MoveInResponse(room.getRoomId(), room.getRoomCode(),
                room.getInitialElectricMeter(), room.getInitialWaterMeter());
    }

    /** Ghi chi so khi tra phong: luu nhu chi so cuoi cua ky duoc chon. */
    @Transactional
    public ReadingResponse recordMoveOut(MoveOutRequest req) {
        return recordReading(new ReadingRequest(req.periodId(), req.roomId(), null, req.electric(),
                null, req.water(), req.recordedByUserId(), req.electricPhotoUrl(), req.waterPhotoUrl()));
    }

    // ------------------------------------------------------------------ helpers

    /** [oldElectric, oldWater]: tu request, neu khong co thi lay ky truoc, neu khong co thi chi so ban dau cua phong. */
    private int[] resolveOldValues(Room room, UtilityReadingPeriod period, Integer reqElectric, Integer reqWater) {
        int oldE;
        int oldW;
        var prev = readingRepo.findFirstByRoomRoomIdAndPeriodEndDateLessThanOrderByPeriodEndDateDesc(
                room.getRoomId(), period.getStartDate());
        oldE = prev.map(MeterReading::getNewElectric).orElse(room.getInitialElectricMeter());
        oldW = prev.map(MeterReading::getNewWater).orElse(room.getInitialWaterMeter());
        if (reqElectric != null) {
            oldE = reqElectric;
        }
        if (reqWater != null) {
            oldW = reqWater;
        }
        return new int[] {oldE, oldW};
    }

    private void addPhotoIfPresent(MeterReading r, String type, String url) {
        if (url != null && !url.isBlank()) {
            savePhoto(r, type, url.trim());
        }
    }

    private MeterPhoto savePhoto(MeterReading r, String type, String url) {
        MeterPhoto ph = new MeterPhoto();
        ph.setReading(r);
        ph.setMeterType(type);
        ph.setPhotoUrl(url);
        return photoRepo.save(ph);
    }

    private static LocalDate orMin(LocalDate d) {
        return d == null ? LocalDate.of(1900, 1, 1) : d;
    }

    private static LocalDate orMax(LocalDate d) {
        return d == null ? LocalDate.of(9999, 12, 31) : d;
    }

    private static int usedOf(Integer newVal, Integer oldVal) {
        return newVal - oldVal;
    }

    private UtilityReadingPeriod findPeriod(Integer id) {
        return periodRepo.findById(id).orElseThrow(() -> notFound("Ky ghi so " + id));
    }

    private MeterReading findReading(Integer id) {
        return readingRepo.findById(id).orElseThrow(() -> notFound("Chi so " + id));
    }

    private <T> T ref(Class<T> type, Integer id) {
        return em.getReference(type, id);
    }

    private PeriodResponse toResponse(UtilityReadingPeriod p) {
        return new PeriodResponse(p.getPeriodId(), p.getProperty().getPropertyId(), p.getPeriodName(),
                p.getStartDate(), p.getEndDate(), Boolean.TRUE.equals(p.getIsClosed()));
    }

    private ReadingResponse toResponse(MeterReading r) {
        return new ReadingResponse(r.getReadingId(), r.getPeriod().getPeriodId(), r.getRoom().getRoomId(),
                r.getRoom().getRoomCode(), r.getOldElectric(), r.getNewElectric(),
                usedOf(r.getNewElectric(), r.getOldElectric()), r.getOldWater(), r.getNewWater(),
                usedOf(r.getNewWater(), r.getOldWater()), r.getReadingDate(),
                r.getRecordedByUser().getUserId(), Boolean.TRUE.equals(r.getIsVerified()));
    }

    private PhotoResponse toResponse(MeterPhoto p) {
        return new PhotoResponse(p.getPhotoId(), p.getReading().getReadingId(), p.getMeterType(), p.getPhotoUrl());
    }

    private AlertResponse toResponse(MeterAnomalyAlert a) {
        Room room = a.getReading().getRoom();
        return new AlertResponse(a.getAlertId(), a.getReading().getReadingId(), room.getRoomId(),
                room.getRoomCode(), a.getUtilityType(), a.getIncreasePercentage(), a.getAlertMessage(),
                Boolean.TRUE.equals(a.getIsResolved()));
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Khong tim thay " + what);
    }
}
