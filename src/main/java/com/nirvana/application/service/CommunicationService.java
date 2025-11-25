package com.nirvana.application.service;

import com.nirvana.application.model.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CommunicationService {

    NotificationResponse sendNotification(Long userId, NotificationSendRequest request);

    NotificationListResponse listNotifications(Long userId, Pageable pageable);

    NotificationResponse markNotificationRead(Long notificationId, Long userId);

    NotificationTemplateResponse upsertTemplate(NotificationTemplateRequest request);

    List<NotificationTemplateResponse> listTemplates();

    MessageThreadResponse startThread(Long userId, MessageThreadCreateRequest request);

    MessageThreadListResponse listThreads(Long userId, Pageable pageable);

    ChatMessageListResponse listMessages(Long threadId, Long userId, Pageable pageable);

    ChatMessageResponse postMessage(Long threadId, Long userId, ChatMessageRequest request);

    ChatMessageResponse moderateMessage(Long messageId, Long adminUserId, ModerationDecisionRequest request);
}
