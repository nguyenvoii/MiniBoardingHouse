package com.group7.minihouse.phi.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.scheduler.JobScheduler;
import com.group7.minihouse.shared.scheduler.JobStatus;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC120: xem danh sach job tu dong va chay tay 1 job. Chi LANDLORD (khi app.security.enabled=true). */
@RestController
@RequestMapping("/api/admin/jobs")
public class JobController {

    private final JobScheduler scheduler;

    public JobController(JobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<List<JobStatus>> list() {
        return ApiResponse.ok(scheduler.list());
    }

    @PostMapping("/{name}/run")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<JobStatus> run(@PathVariable String name) {
        return ApiResponse.ok(scheduler.runNow(name));
    }
}
