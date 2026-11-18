package com.group7.minihouse.toan.controller;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.group7.minihouse.toan.dto.MeterDtos.*;
import com.group7.minihouse.toan.service.MeterReadingService;

/** API chi so dien nuoc (UC 53-60). */
@RestController
@RequestMapping("/api")
public class MeterReadingController {

    private final MeterReadingService service;

    public MeterReadingController(MeterReadingService service) {
        this.service = service;
    }

    // UC 53
    @PostMapping("/meter-periods")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodResponse createPeriod(@RequestBody PeriodRequest req) {
        return service.createPeriod(req);
    }

    @GetMapping("/meter-periods")
    public List<PeriodResponse> listPeriods(@RequestParam("propertyId") Integer propertyId) {
        return service.listPeriods(propertyId);
    }

    @PatchMapping("/meter-periods/{id}/closed")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public PeriodResponse closePeriod(@PathVariable("id") Integer id,
                                      @RequestParam(value = "value", defaultValue = "true") boolean value) {
        return service.closePeriod(id, value);
    }

    // UC 54, 55
    @PostMapping("/meter-readings")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','METER_READER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingResponse record(@RequestBody ReadingRequest req,
                                  @org.springframework.security.core.annotation.AuthenticationPrincipal
                                  com.group7.minihouse.shared.security.AuthUser actor) {
        // da dang nhap thi ghi dung nguoi dang nhap, khong tin recordedByUserId client gui
        ReadingRequest r = actor == null ? req : new ReadingRequest(req.periodId(), req.roomId(), req.oldElectric(),
                req.newElectric(), req.oldWater(), req.newWater(), actor.userId(), req.electricPhotoUrl(),
                req.waterPhotoUrl());
        return service.recordReading(r);
    }

    @GetMapping("/meter-periods/{periodId}/readings")
    public List<ReadingResponse> listReadings(@PathVariable("periodId") Integer periodId) {
        return service.listReadings(periodId);
    }

    /** Phong can ghi chi so cua ky (theo hop dong chong len ky, khong chi hop dong hom nay). Cho ca METER_READER. */
    @GetMapping("/meter-periods/{periodId}/missing-rooms")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','METER_READER')")
    public List<MissingRoom> missingRooms(@PathVariable("periodId") Integer periodId) {
        return service.missingRooms(periodId);
    }

    @GetMapping("/meter-readings/{id}")
    public ReadingResponse getReading(@PathVariable("id") Integer id) {
        return service.getReading(id);
    }

    @PostMapping("/meter-readings/{id}/photos")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','METER_READER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse addPhoto(@PathVariable("id") Integer id, @RequestBody PhotoRequest req) {
        return service.addPhoto(id, req);
    }

    @GetMapping("/meter-readings/{id}/photos")
    public List<PhotoResponse> listPhotos(@PathVariable("id") Integer id) {
        return service.listPhotos(id);
    }

    // UC 56
    @PostMapping("/meter-readings/{id}/confirm")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ReadingResponse confirm(@PathVariable("id") Integer id) {
        return service.confirmReading(id);
    }

    @PutMapping("/meter-readings/{id}/adjust")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ReadingResponse adjust(@PathVariable("id") Integer id, @RequestBody AdjustRequest req) {
        return service.adjustReading(id, req);
    }

    @GetMapping("/meter-readings/{id}/adjustments")
    public List<AdjustmentLogResponse> adjustments(@PathVariable("id") Integer id) {
        return service.listAdjustments(id);
    }

    // UC 57
    @GetMapping("/meter-alerts")
    public List<AlertResponse> alerts(@RequestParam("propertyId") Integer propertyId,
                                      @RequestParam(value = "resolved", required = false) Boolean resolved,
                                      @RequestParam(value = "roomId", required = false) Integer roomId) {
        return service.searchAlerts(propertyId, resolved, roomId);
    }

    @PostMapping("/meter-alerts/{id}/resolve")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public AlertResponse resolve(@PathVariable("id") Integer id) {
        return service.resolveAlert(id);
    }

    // UC 58
    @GetMapping("/rooms/{roomId}/consumption")
    public ConsumptionHistory roomHistory(
            @PathVariable("roomId") Integer roomId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.roomHistory(roomId, from, to);
    }

    // UC 59: du lieu bieu do so sanh cac thang
    @GetMapping("/properties/{propertyId}/consumption-comparison")
    public List<ConsumptionPoint> comparison(
            @PathVariable("propertyId") Integer propertyId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.propertyComparison(propertyId, from, to);
    }

    // UC 60
    @PostMapping("/meter-readings/move-in")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','METER_READER')")
    public MoveInResponse moveIn(@RequestBody MoveInRequest req) {
        return service.recordMoveIn(req);
    }

    @PostMapping("/meter-readings/move-out")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','METER_READER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingResponse moveOut(@RequestBody MoveOutRequest req,
                                   @org.springframework.security.core.annotation.AuthenticationPrincipal
                                   com.group7.minihouse.shared.security.AuthUser actor) {
        MoveOutRequest r = actor == null ? req : new MoveOutRequest(req.roomId(), req.periodId(), req.electric(),
                req.water(), actor.userId(), req.electricPhotoUrl(), req.waterPhotoUrl());
        return service.recordMoveOut(r);
    }
}
