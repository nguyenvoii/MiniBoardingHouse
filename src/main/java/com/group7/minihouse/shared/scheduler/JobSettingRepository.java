package com.group7.minihouse.shared.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSettingRepository extends JpaRepository<JobSetting, String> {
}
