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

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final CommunicationService communicationService;

    @PostMapping("/send")
    public NotificationResponse sendNotification(@Valid @RequestBody NotificationSendRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return communicationService.sendNotification(userId, request);
    }

    @GetMapping("/me")
    public NotificationListResponse myNotifications(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return communicationService.listNotifications(userId, pageable);
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable Long notificationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return communicationService.markNotificationRead(notificationId, userId);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/templates")
    public NotificationTemplateResponse upsertTemplate(@Valid @RequestBody NotificationTemplateRequest request) {
        return communicationService.upsertTemplate(request);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/templates")
    public List<NotificationTemplateResponse> listTemplates() {
        return communicationService.listTemplates();
    }
}
