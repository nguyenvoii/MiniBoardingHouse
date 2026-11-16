package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.OccupancyDtos.ResidentRequest;
import com.group7.minihouse.phi.dto.OccupancyDtos.ResidentResponse;
import com.group7.minihouse.phi.dto.OccupancyDtos.VehicleRequest;
import com.group7.minihouse.phi.dto.OccupancyDtos.VehicleResponse;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomResident;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.TenantVehicle;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xe cua khach thue va nguoi o cung trong phong. Day la nguon SO LUONG THAT cho cac dich vu tinh theo xe
 * (PER_VEHICLE) va theo dau nguoi (PER_PERSON) khi tinh tien thang. Xoa chi la ngung (is_active = false) de giu lich su.
 * Khach thue va phong la bang cua Chi (chua co service) nen doc/ghi qua EntityManager.
 */
@Service
public class OccupancyService {

    static final Set<String> VEHICLE_TYPES = Set.of("MOTORBIKE", "BICYCLE", "ELECTRIC_BIKE", "CAR", "OTHER");

    private final EntityManager em;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public OccupancyService(EntityManager em, ActivityLogService activityLogService, Clock clock) {
        this.em = em;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ xe

    @Transactional(readOnly = true)
    public List<VehicleResponse> vehicles(Integer tenantId) {
        tenant(tenantId);
        return em.createQuery("select v from TenantVehicle v where v.tenant.tenantId = :t and v.isActive = true "
                + "order by v.vehicleId", TenantVehicle.class).setParameter("t", tenantId).getResultList().stream()
                .map(OccupancyService::toResponse).toList();
    }

    @Transactional
    public VehicleResponse addVehicle(Integer tenantId, VehicleRequest req, Integer actorId, String ip) {
        Tenant tenant = tenant(tenantId);
        String plate = plate(req);
        String type = type(req);
        List<TenantVehicle> same = em.createQuery("select v from TenantVehicle v where v.licensePlate = :p",
                TenantVehicle.class).setParameter("p", plate).getResultList();
        TenantVehicle v;
        boolean isNew = same.isEmpty();
        if (isNew) {
            v = new TenantVehicle();
            v.setTenant(tenant);
            v.setLicensePlate(plate);
        } else {
            v = same.get(0);
            if (Boolean.TRUE.equals(v.getIsActive())) {
                throw new BusinessException(HttpStatus.CONFLICT, "Bien so " + plate + " da duoc dang ky"
                        + (v.getTenant().getTenantId().equals(tenantId) ? " cho khach nay" : " cho khach khac"));
            }
            v.setTenant(tenant); // xe cu da ngung dung: dang ky lai (co the cho khach khac)
        }
        v.setVehicleType(type);
        v.setBrand(blank(req.brand()));
        v.setColor(blank(req.color()));
        v.setIsActive(true);
        if (isNew) {
            em.persist(v); // sau khi da gan du cot bat buoc (khoa tu tang chen ngay khi persist)
        }
        audit(actorId, "ADD_VEHICLE", "Dang ky xe " + plate + " cho khach thue " + tenantId, ip);
        return toResponse(v);
    }

    @Transactional
    public VehicleResponse updateVehicle(Integer vehicleId, VehicleRequest req, Integer actorId, String ip) {
        TenantVehicle v = vehicle(vehicleId);
        String plate = plate(req);
        if (!plate.equals(v.getLicensePlate())) {
            long dup = em.createQuery("select count(x) from TenantVehicle x where x.licensePlate = :p and x.vehicleId <> :id",
                    Long.class).setParameter("p", plate).setParameter("id", vehicleId).getSingleResult();
            if (dup > 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "Bien so " + plate + " da ton tai");
            }
            v.setLicensePlate(plate);
        }
        v.setVehicleType(type(req));
        v.setBrand(blank(req.brand()));
        v.setColor(blank(req.color()));
        audit(actorId, "UPDATE_VEHICLE", "Sua xe " + vehicleId, ip);
        return toResponse(v);
    }

    @Transactional
    public void removeVehicle(Integer vehicleId, Integer actorId, String ip) {
        TenantVehicle v = vehicle(vehicleId);
        v.setIsActive(false);
        audit(actorId, "REMOVE_VEHICLE", "Ngung dung xe " + v.getLicensePlate(), ip);
    }

    // ------------------------------------------------------------------ nguoi o cung

    @Transactional(readOnly = true)
    public List<ResidentResponse> residents(Integer roomId) {
        room(roomId);
        return em.createQuery("select r from RoomResident r where r.room.roomId = :x and r.isActive = true "
                + "order by r.residentId", RoomResident.class).setParameter("x", roomId).getResultList().stream()
                .map(OccupancyService::toResponse).toList();
    }

    @Transactional
    public ResidentResponse addResident(Integer roomId, ResidentRequest req, Integer actorId, String ip) {
        Room room = room(roomId);
        String name = name(req);
        long cohabitants = em.createQuery("select count(r) from RoomResident r where r.room.roomId = :x "
                + "and r.isActive = true", Long.class).setParameter("x", roomId).getSingleResult();
        // nguoi dung ten hop dong luon la 1 nguoi, nen tong so nguoi = 1 + nguoi o cung
        if (room.getMaxOccupants() != null && cohabitants + 2 > room.getMaxOccupants()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Phong " + room.getRoomCode() + " chi cho toi da "
                    + room.getMaxOccupants() + " nguoi (tinh ca nguoi dung ten hop dong)");
        }
        RoomResident r = new RoomResident();
        r.setRoom(room);
        fill(r, req, name);
        r.setStartDate(req.startDate() != null ? req.startDate() : LocalDate.now(clock));
        r.setIsActive(true);
        em.persist(r);
        audit(actorId, "ADD_RESIDENT", "Them nguoi o cung " + name + " vao phong " + room.getRoomCode(), ip);
        return toResponse(r);
    }

    @Transactional
    public ResidentResponse updateResident(Integer residentId, ResidentRequest req, Integer actorId, String ip) {
        RoomResident r = resident(residentId);
        fill(r, req, name(req));
        if (req.startDate() != null) {
            r.setStartDate(req.startDate());
        }
        audit(actorId, "UPDATE_RESIDENT", "Sua nguoi o cung " + residentId, ip);
        return toResponse(r);
    }

    @Transactional
    public void removeResident(Integer residentId, Integer actorId, String ip) {
        RoomResident r = resident(residentId);
        r.setIsActive(false);
        r.setEndDate(LocalDate.now(clock));
        audit(actorId, "REMOVE_RESIDENT", "Nguoi o cung " + r.getFullName() + " roi phong " + r.getRoom().getRoomCode(), ip);
    }

    // ------------------------------------------------------------------ noi bo

    private Tenant tenant(Integer id) {
        Tenant t = em.find(Tenant.class, id);
        if (t == null) {
            throw BusinessException.notFound("Khach thue " + id);
        }
        return t;
    }

    private Room room(Integer id) {
        Room r = em.find(Room.class, id);
        if (r == null) {
            throw BusinessException.notFound("Phong " + id);
        }
        return r;
    }

    private TenantVehicle vehicle(Integer id) {
        TenantVehicle v = em.find(TenantVehicle.class, id);
        if (v == null) {
            throw BusinessException.notFound("Xe " + id);
        }
        return v;
    }

    private RoomResident resident(Integer id) {
        RoomResident r = em.find(RoomResident.class, id);
        if (r == null || !Boolean.TRUE.equals(r.getIsActive())) {
            throw BusinessException.notFound("Nguoi o cung " + id);
        }
        return r;
    }

    static String plate(VehicleRequest req) {
        if (req == null || req.licensePlate() == null || req.licensePlate().isBlank()) {
            throw bad("Can bien so xe");
        }
        String p = req.licensePlate().trim().toUpperCase(Locale.ROOT);
        if (p.length() > 20) {
            throw bad("Bien so toi da 20 ky tu");
        }
        return p;
    }

    static String type(VehicleRequest req) {
        String t = req.vehicleType() == null ? "MOTORBIKE" : req.vehicleType().trim().toUpperCase(Locale.ROOT);
        if (!VEHICLE_TYPES.contains(t)) {
            throw bad("Loai xe phai la " + VEHICLE_TYPES);
        }
        return t;
    }

    static String name(ResidentRequest req) {
        if (req == null || req.fullName() == null || req.fullName().isBlank()) {
            throw bad("Can ho ten nguoi o cung");
        }
        String n = req.fullName().trim();
        if (n.length() > 100) {
            throw bad("Ho ten toi da 100 ky tu");
        }
        return n;
    }

    private static void fill(RoomResident r, ResidentRequest req, String name) {
        r.setFullName(name);
        r.setPhoneNumber(blank(req.phoneNumber()));
        r.setIdentityNumber(blank(req.identityNumber()));
        r.setRelationshipWithPrimary(blank(req.relationship()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void audit(Integer actorId, String action, String description, String ip) {
        if (actorId != null) {
            activityLogService.log(actorId, action, description, ip);
        }
    }

    private static BusinessException bad(String m) {
        return new BusinessException(HttpStatus.BAD_REQUEST, m);
    }

    private static VehicleResponse toResponse(TenantVehicle v) {
        return new VehicleResponse(v.getVehicleId(), v.getTenant().getTenantId(), v.getLicensePlate(), v.getVehicleType(),
                v.getBrand(), v.getColor());
    }

    private static ResidentResponse toResponse(RoomResident r) {
        return new ResidentResponse(r.getResidentId(), r.getRoom().getRoomId(), r.getFullName(), r.getPhoneNumber(),
                r.getIdentityNumber(), r.getRelationshipWithPrimary(), r.getStartDate());
    }
}
