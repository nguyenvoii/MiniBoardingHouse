package com.group7.minihouse.toan.controller;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.group7.minihouse.toan.dto.RateDtos.*;
import com.group7.minihouse.toan.service.ServiceRateService;

/** API don gia & phi dich vu (UC 61-64). */
@RestController
@RequestMapping("/api")
public class ServiceRateController {

    private final ServiceRateService service;

    public ServiceRateController(ServiceRateService service) {
        this.service = service;
    }

    // UC 61, 64: bang gia dien nuoc
    @PostMapping("/service-rate-plans")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse createPlan(@RequestBody PlanRequest req) {
        return service.createPlan(req);
    }

    @PutMapping("/service-rate-plans/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public PlanResponse updatePlan(@PathVariable("id") Integer id, @RequestBody PlanRequest req) {
        return service.updatePlan(id, req);
    }

    @GetMapping("/service-rate-plans")
    public List<PlanResponse> listPlans() {
        return service.listPlans();
    }

    @GetMapping("/service-rate-plans/effective")
    public PlanResponse effective(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.effectivePlan(date);
    }

    // UC 62: phi dich vu theo khu
    @PostMapping("/property-service-rates")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceRateResponse createServiceRate(@RequestBody ServiceRateRequest req) {
        return service.createServiceRate(req);
    }

    @PutMapping("/property-service-rates/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ServiceRateResponse updateServiceRate(@PathVariable("id") Integer id, @RequestBody ServiceRateRequest req) {
        return service.updateServiceRate(id, req);
    }

    @DeleteMapping("/property-service-rates/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteServiceRate(@PathVariable("id") Integer id) {
        service.deleteServiceRate(id);
    }

    @GetMapping("/property-service-rates")
    public List<ServiceRateResponse> listServiceRates(
            @RequestParam("propertyId") Integer propertyId,
            @RequestParam(value = "activeOnly", required = false) Boolean activeOnly) {
        return service.listServiceRates(propertyId, activeOnly);
    }

    // UC 63: phu thu & mien giam theo phong
    @PostMapping("/room-custom-rates")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomRateResponse createCustomRate(@RequestBody CustomRateRequest req) {
        return service.createCustomRate(req);
    }

    @PutMapping("/room-custom-rates/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public CustomRateResponse updateCustomRate(@PathVariable("id") Integer id, @RequestBody CustomRateRequest req) {
        return service.updateCustomRate(id, req);
    }

    @DeleteMapping("/room-custom-rates/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomRate(@PathVariable("id") Integer id) {
        service.deleteCustomRate(id);
    }

    @GetMapping("/room-custom-rates")
    public List<CustomRateResponse> listCustomRates(@RequestParam("roomId") Integer roomId) {
        return service.listCustomRates(roomId);
    }

    // UC 64: lich su doi gia
    @GetMapping("/rate-histories")
    public List<RateHistoryResponse> history(@RequestParam("propertyId") Integer propertyId,
                                             @RequestParam(value = "serviceName", required = false) String serviceName) {
        return service.history(propertyId, serviceName);
    }
}
