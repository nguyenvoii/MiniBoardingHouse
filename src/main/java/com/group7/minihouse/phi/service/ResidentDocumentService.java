package com.group7.minihouse.phi.service;

import com.group7.minihouse.shared.entity.RoomResident;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.storage.FileStorage;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Giay to cua nguoi o cung: anh CCCD hai mat va danh sach nguoi o cung can nhac khai bao tam tru. */
@Service
public class ResidentDocumentService {

    static final String FOLDER = "resident-id-cards";
    static final long MAX_BYTES = 2L * 1024 * 1024;
    /** Bao truoc bay ngay so voi han khai bao tam tru. */
    static final int WARN_DAYS = 7;

    public record TempResidenceDue(Integer residentId, Integer roomId, String roomCode, String fullName,
                                   LocalDate dueDate, boolean overdue) {}

    private final EntityManager em;
    private final FileStorage storage;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public ResidentDocumentService(EntityManager em, FileStorage storage, ActivityLogService activityLogService,
                                   Clock clock) {
        this.em = em;
        this.storage = storage;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    static String urlOf(Integer residentId, String name) {
        return "/api/residents/" + residentId + "/id-card/photos/" + name;
    }

    /** Luu anh CCCD (mot hoac hai mat; mat nao khong gui thi giu nguyen anh cu). Anh cu bi xoa khoi o dia. */
    @Transactional
    public RoomResident uploadIdCard(Integer residentId, byte[] front, byte[] back, Integer actorId, String ip) {
        boolean hasFront = front != null && front.length > 0;
        boolean hasBack = back != null && back.length > 0;
        if (!hasFront && !hasBack) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Chon it nhat mot anh can cuoc");
        }
        RoomResident r = resident(residentId);
        String oldFront = r.getIdFrontImageUrl();
        String oldBack = r.getIdBackImageUrl();
        String newFront = null;
        String newBack = null;
        try {
            if (hasFront) {
                newFront = storage.saveImage(FOLDER, front, MAX_BYTES);
                r.setIdFrontImageUrl(urlOf(residentId, newFront));
            }
            if (hasBack) {
                newBack = storage.saveImage(FOLDER, back, MAX_BYTES);
                r.setIdBackImageUrl(urlOf(residentId, newBack));
            }
            em.flush();
        } catch (RuntimeException failure) {
            discard(newFront);
            discard(newBack);
            throw failure;
        }
        if (hasFront) {
            discardUrl(residentId, oldFront);
        }
        if (hasBack) {
            discardUrl(residentId, oldBack);
        }
        if (actorId != null) {
            activityLogService.log(actorId, "UPLOAD_RESIDENT_ID_CARD", "Tai anh CCCD cua nguoi o cung " + residentId, ip);
        }
        return r;
    }

    /** Duong dan file anh neu dung la anh cua nguoi o cung nay. */
    @Transactional(readOnly = true)
    public Optional<Path> photo(Integer residentId, String name) {
        RoomResident r = resident(residentId);
        String url = urlOf(residentId, name);
        if (!url.equals(r.getIdFrontImageUrl()) && !url.equals(r.getIdBackImageUrl())) {
            return Optional.empty();
        }
        return storage.find(FOLDER, name);
    }

    /** Nguoi o cung dang o, chua khai bao tam tru, da qua han hoac con toi da WARN_DAYS ngay la toi han. */
    @Transactional(readOnly = true)
    public List<TempResidenceDue> tempResidenceDue(Integer propertyId) {
        LocalDate today = LocalDate.now(clock);
        String jpql = "select r from RoomResident r where r.isActive = true and r.tempResidenceRegisteredOn is null"
                + " and r.startDate <= :limit" + (propertyId != null ? " and r.room.property.propertyId = :pid" : "")
                + " order by r.startDate, r.residentId";
        var q = em.createQuery(jpql, RoomResident.class)
                .setParameter("limit", today.minusDays(OccupancyService.TEMP_RESIDENCE_DAYS - WARN_DAYS));
        if (propertyId != null) {
            q.setParameter("pid", propertyId);
        }
        return q.getResultList().stream().map(r -> toDue(r, today)).toList();
    }

    static TempResidenceDue toDue(RoomResident r, LocalDate today) {
        LocalDate due = r.getStartDate().plusDays(OccupancyService.TEMP_RESIDENCE_DAYS);
        return new TempResidenceDue(r.getResidentId(), r.getRoom().getRoomId(), r.getRoom().getRoomCode(),
                r.getFullName(), due, today.isAfter(due));
    }

    private RoomResident resident(Integer id) {
        RoomResident r = em.find(RoomResident.class, id);
        if (r == null) {
            throw BusinessException.notFound("Nguoi o cung");
        }
        return r;
    }

    private void discardUrl(Integer residentId, String url) {
        String prefix = urlOf(residentId, "");
        if (url != null && url.startsWith(prefix)) { // chi xoa anh cua chinh nguoi nay
            discard(url.substring(prefix.length()));
        }
    }

    private void discard(String name) {
        if (name == null) {
            return;
        }
        try {
            storage.delete(FOLDER, name);
        } catch (RuntimeException ignore) {
            // loi don dep khong duoc lam hong viec luu anh moi
        }
    }
}
