package com.nirvana.application.repository;


import com.nirvana.application.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByUsername(String username);

    boolean existsByUsername(String username);

    java.util.Optional<User> findByEmail(String email);

    java.util.Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    long countByDataErasureRequestedAtIsNotNullAndDataErasedAtIsNull();

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = """
            UPDATE app_user SET last_login_at=GREATEST(COALESCE(last_login_at,UTC_TIMESTAMP(6)),UTC_TIMESTAMP(6))
            WHERE id=:userId AND data_erased_at IS NULL
            """, nativeQuery = true)
    void recordSignIn(@org.springframework.data.repository.query.Param("userId") long userId);

    /** Atomic balance change, so concurrent bookings for one customer cannot lose points. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "update User u set u.loyaltyPoints = coalesce(u.loyaltyPoints, 0) + :points where u.id = :userId")
    int addLoyaltyPoints(@org.springframework.data.repository.query.Param("userId") Long userId,
                         @org.springframework.data.repository.query.Param("points") int points);
}
