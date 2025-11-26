package com.nirvana.application.repository;

import com.nirvana.application.model.enums.spa.UserSpaPackageStatus;
import com.nirvana.application.model.spa.UserSpaPackageSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface UserSpaPackageSubscriptionRepository extends JpaRepository<UserSpaPackageSubscription, Long> {

    List<UserSpaPackageSubscription> findByUserIdOrderByPurchaseDateDesc(Long userId);

    Optional<UserSpaPackageSubscription> findByIdAndUserId(Long id, Long userId);

    Optional<UserSpaPackageSubscription> findFirstByUserIdAndSpaIdAndStatus(Long userId, Long spaId, UserSpaPackageStatus status);

    Optional<UserSpaPackageSubscription> findFirstByUserIdAndSpaIdAndStatusIn(Long userId, Long spaId, List<UserSpaPackageStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSpaPackageSubscription s where s.user.id = :userId and s.spa.id = :spaId and s.status = :status")
    Optional<UserSpaPackageSubscription> findActiveForUpdate(@Param("userId") Long userId,
                                                             @Param("spaId") Long spaId,
                                                             @Param("status") UserSpaPackageStatus status);
}
