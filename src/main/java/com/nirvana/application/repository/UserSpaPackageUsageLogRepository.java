package com.nirvana.application.repository;

import com.nirvana.application.model.spa.UserSpaPackageUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSpaPackageUsageLogRepository extends JpaRepository<UserSpaPackageUsageLog, Long> {

    List<UserSpaPackageUsageLog> findBySubscriptionIdOrderByUsageDateDesc(Long subscriptionId);
}
