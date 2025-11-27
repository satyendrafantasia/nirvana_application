package com.nirvana.application.repository;

import com.nirvana.application.model.NotificationLog;
import com.nirvana.application.model.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<NotificationLog> findByIdAndUserId(Long id, Long userId);

    long countByCreatedAtAfter(OffsetDateTime threshold);

    long countByStatusAndCreatedAtAfter(NotificationStatus status, OffsetDateTime threshold);

    long countByStatusInAndCreatedAtAfter(Collection<NotificationStatus> statuses, OffsetDateTime threshold);
}
