package com.nirvana.application.repository;

import com.nirvana.application.model.ChatMessage;
import com.nirvana.application.model.enums.ModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByThreadIdAndThreadUserIdOrderByCreatedAtAsc(Long threadId, Long userId, Pageable pageable);

    Optional<ChatMessage> findByIdAndThreadUserId(Long id, Long userId);

    List<ChatMessage> findTop5ByModerationStatusOrderByCreatedAtAsc(ModerationStatus status);
}
