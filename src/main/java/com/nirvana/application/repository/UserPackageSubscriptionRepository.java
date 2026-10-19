package com.nirvana.application.repository;

import com.nirvana.application.model.UserPackageSubscription;
import com.nirvana.application.model.enums.PackageStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserPackageSubscriptionRepository extends JpaRepository<UserPackageSubscription, Long> {

    java.util.List<UserPackageSubscription> findByUserIdOrderByPurchaseDateDesc(Long userId);
    Optional<UserPackageSubscription> findByRazorpayOrderId(String orderId);
    @Query("select s.id from UserPackageSubscription s where s.razorpayOrderId = :orderId")
    Optional<Long> findIdByOrder(@Param("orderId") String orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserPackageSubscription s where s.id = :id")
    Optional<UserPackageSubscription> findForUpdate(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserPackageSubscription s where s.id = :id and s.user.id = :userId")
    Optional<UserPackageSubscription> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"servicePackage", "servicePackage.eligibleSpas"})
    Optional<UserPackageSubscription> findByUserIdAndStatus(Long userId, PackageStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserPackageSubscription s join fetch s.servicePackage p left join fetch p.eligibleSpas where s.user.id = :userId and s.status = :status")
    Optional<UserPackageSubscription> findByUserIdAndStatusForUpdate(@Param("userId") Long userId,
                                                                     @Param("status") PackageStatus status);
}
