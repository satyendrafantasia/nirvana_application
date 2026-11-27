package com.nirvana.application.repository;

import com.nirvana.application.model.MessageThread;
import com.nirvana.application.model.enums.MessageThreadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageThreadRepository extends JpaRepository<MessageThread, Long> {

    Page<MessageThread> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<MessageThread> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, MessageThreadStatus status);
}
