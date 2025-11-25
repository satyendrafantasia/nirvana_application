package com.nirvana.application.controller;

import com.nirvana.application.model.dto.*;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.CommunicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Validated
public class MessagingController {

    private final CommunicationService communicationService;

    @PostMapping("/threads")
    public MessageThreadResponse startThread(@Valid @RequestBody MessageThreadCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return communicationService.startThread(userId, request);
    }

    @GetMapping("/threads")
    public MessageThreadListResponse listThreads(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return communicationService.listThreads(userId, pageable);
    }

    @GetMapping("/threads/{threadId}/messages")
    public ChatMessageListResponse listMessages(
            @PathVariable Long threadId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return communicationService.listMessages(threadId, userId, pageable);
    }

    @PostMapping("/threads/{threadId}/messages")
    public ChatMessageResponse postMessage(
            @PathVariable Long threadId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return communicationService.postMessage(threadId, userId, request);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/messages/{messageId}/moderate")
    public ChatMessageResponse moderate(
            @PathVariable Long messageId,
            @Valid @RequestBody ModerationDecisionRequest request
    ) {
        Long adminUserId = SecurityUtils.getCurrentUserId();
        return communicationService.moderateMessage(messageId, adminUserId, request);
    }
}
