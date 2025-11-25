package com.nirvana.application.repository;

import com.nirvana.application.model.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<NotificationLog> findByIdAndUserId(Long id, Long userId);
}
