package com.nirvana.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.*;
import com.nirvana.application.model.enums.*;
import com.nirvana.application.repository.*;
import com.nirvana.application.service.CommunicationService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationServiceImpl implements CommunicationService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final MessageThreadRepository messageThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public NotificationResponse sendNotification(Long userId, NotificationSendRequest request) {
        User user = resolveUser(userId);
        NotificationTemplate template = templateRepository.findByCodeAndChannelAndEnabledIsTrue(request.getTemplateCode(), request.getChannel())
                .orElseThrow(() -> new EntityNotFoundException("Notification template not found or disabled"));

        String destination = resolveDestination(user, request.getDestination(), request.getChannel());
        if (!StringUtils.hasText(destination)) {
            throw new IllegalStateException("Notification destination missing for channel " + request.getChannel());
        }

        Map<String, String> variables = request.getVariables() != null ? new HashMap<>(request.getVariables()) : new HashMap<>();
        variables.putIfAbsent("userName", user.getDisplayName() != null ? user.getDisplayName() : user.getName());
        variables.putIfAbsent("userEmail", user.getEmail());
        variables.putIfAbsent("userPhone", user.getPhone());

        String title = render(template.getSubject(), variables);
        String body = render(template.getBody(), variables);

        NotificationLog log = NotificationLog.builder()
                .user(user)
                .channel(request.getChannel())
                .status(NotificationStatus.SENT)
                .templateCode(template.getCode())
                .destination(destination)
                .title(title)
                .message(body)
                .payloadJson(serializePayload(variables))
                .sentAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        NotificationLog saved = notificationLogRepository.save(log);
        log.info("Queued notification {} for user {} via {}", saved.getId(), userId, request.getChannel());
        return toResponse(saved);
    }

    @Override
    public NotificationListResponse listNotifications(Long userId, Pageable pageable) {
        Page<NotificationLog> page = notificationLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return NotificationListResponse.builder()
                .notifications(page.map(this::toResponse).toList())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();
    }

    @Override
    @Transactional
    public NotificationResponse markNotificationRead(Long notificationId, Long userId) {
        NotificationLog logEntry = notificationLogRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        if (logEntry.getReadAt() == null) {
            logEntry.setReadAt(OffsetDateTime.now(ZoneOffset.UTC));
            notificationLogRepository.save(logEntry);
        }
        return toResponse(logEntry);
    }

    @Override
    @Transactional
    public NotificationTemplateResponse upsertTemplate(NotificationTemplateRequest request) {
        NotificationTemplate template = templateRepository
                .findByCodeAndChannel(request.getCode(), request.getChannel())
                .orElse(NotificationTemplate.builder()
                        .code(request.getCode())
                        .channel(request.getChannel())
                        .build());

        template.setName(request.getName());
        template.setSubject(request.getSubject());
        template.setBody(request.getBody());
        template.setLocale(request.getLocale());
        template.setDescription(request.getDescription());
        if (request.getEnabled() != null) {
            template.setEnabled(request.getEnabled());
        }

        NotificationTemplate saved = templateRepository.save(template);
        return toTemplateResponse(saved);
    }

    @Override
    public List<NotificationTemplateResponse> listTemplates() {
        return templateRepository.findAllByEnabledIsTrue().stream()
                .map(this::toTemplateResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MessageThreadResponse startThread(Long userId, MessageThreadCreateRequest request) {
        User user = resolveUser(userId);
        Booking booking = null;
        if (request.getBookingId() != null) {
            booking = bookingRepository.findByIdAndUserId(request.getBookingId(), userId)
                    .orElseThrow(() -> new EntityNotFoundException("Booking not found for user"));
        }

        MessageThread thread = MessageThread.builder()
                .user(user)
                .booking(booking)
                .subject(request.getSubject())
                .lastMessageAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        MessageThread savedThread = messageThreadRepository.save(thread);

        if (StringUtils.hasText(request.getInitialMessage())) {
            ChatMessage message = buildMessage(savedThread, user, SenderType.USER, request.getInitialMessage(), request.getAttachments());
            chatMessageRepository.save(message);
            savedThread.setLastMessageAt(message.getCreatedAt());
            savedThread.setUnreadCount(savedThread.getUnreadCount() + 1);
        }

        messageThreadRepository.save(savedThread);
        return toThreadResponse(savedThread);
    }

    @Override
    public MessageThreadListResponse listThreads(Long userId, Pageable pageable) {
        Page<MessageThread> page = messageThreadRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);
        return MessageThreadListResponse.builder()
                .threads(page.map(this::toThreadResponse).toList())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();
    }

    @Override
    public ChatMessageListResponse listMessages(Long threadId, Long userId, Pageable pageable) {
        Page<ChatMessage> page = chatMessageRepository.findByThreadIdAndThreadUserIdOrderByCreatedAtAsc(threadId, userId, pageable);
        return ChatMessageListResponse.builder()
                .messages(page.map(this::toMessageResponse).toList())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();
    }

    @Override
    @Transactional
    public ChatMessageResponse postMessage(Long threadId, Long userId, ChatMessageRequest request) {
        MessageThread thread = messageThreadRepository.findByIdAndUserId(threadId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Thread not found for user"));
        if (thread.getStatus() == MessageThreadStatus.CLOSED) {
            throw new IllegalStateException("Thread is closed");
        }

        User user = resolveUser(userId);
        ChatMessage message = buildMessage(thread, user, SenderType.USER, request.getContent(), request.getAttachments());
        ChatMessage saved = chatMessageRepository.save(message);

        thread.setLastMessageAt(saved.getCreatedAt());
        thread.setUnreadCount(thread.getUnreadCount() + 1);
        messageThreadRepository.save(thread);
        return toMessageResponse(saved);
    }

    @Override
    @Transactional
    public ChatMessageResponse moderateMessage(Long messageId, Long adminUserId, ModerationDecisionRequest request) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found"));

        message.setModerationStatus(request.getStatus());
        message.setModerationNote(request.getNote());
        message.setModeratedAt(OffsetDateTime.now(ZoneOffset.UTC));

        if (message.getAttachments() != null) {
            message.getAttachments().forEach(attachment -> {
                attachment.setModerationStatus(request.getStatus());
                attachment.setModerationNote(request.getNote());
            });
        }

        ChatMessage saved = chatMessageRepository.save(message);
        log.info("Message {} moderated by {} with status {}", messageId, adminUserId, request.getStatus());
        return toMessageResponse(saved);
    }

    private ChatMessage buildMessage(MessageThread thread, User sender, SenderType senderType, String content, List<MessageAttachmentRequest> attachments) {
        ChatMessage message = ChatMessage.builder()
                .thread(thread)
                .sender(sender)
                .senderType(senderType)
                .content(content)
                .moderationStatus(ModerationStatus.PENDING)
                .build();

        if (attachments != null && !attachments.isEmpty()) {
            List<MessageAttachment> mapped = attachments.stream()
                    .map(att -> MessageAttachment.builder()
                            .message(message)
                            .fileUrl(att.getFileUrl())
                            .fileName(att.getFileName())
                            .contentType(att.getContentType())
                            .sizeBytes(att.getSizeBytes())
                            .build())
                    .collect(Collectors.toList());
            message.setAttachments(mapped);
        }
        return message;
    }

    private String render(String text, Map<String, String> variables) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String rendered = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            rendered = rendered.replace(placeholder, Optional.ofNullable(entry.getValue()).orElse(""));
        }
        return rendered;
    }

    private String serializePayload(Map<String, String> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize notification payload: {}", e.getMessage());
            return "{}";
        }
    }

    private String resolveDestination(User user, String requested, NotificationChannel channel) {
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        return switch (channel) {
            case EMAIL, PUSH -> user.getEmail();
            case SMS, WHATSAPP -> user.getPhone();
        };
    }

    private NotificationResponse toResponse(NotificationLog logEntry) {
        return NotificationResponse.builder()
                .id(logEntry.getId())
                .channel(logEntry.getChannel())
                .status(logEntry.getStatus())
                .templateCode(logEntry.getTemplateCode())
                .destination(logEntry.getDestination())
                .title(logEntry.getTitle())
                .message(logEntry.getMessage())
                .sentAt(logEntry.getSentAt())
                .readAt(logEntry.getReadAt())
                .createdAt(logEntry.getCreatedAt())
                .build();
    }

    private NotificationTemplateResponse toTemplateResponse(NotificationTemplate template) {
        return NotificationTemplateResponse.builder()
                .id(template.getId())
                .code(template.getCode())
                .channel(template.getChannel())
                .name(template.getName())
                .subject(template.getSubject())
                .body(template.getBody())
                .locale(template.getLocale())
                .description(template.getDescription())
                .enabled(template.getEnabled())
                .build();
    }

    private MessageThreadResponse toThreadResponse(MessageThread thread) {
        return MessageThreadResponse.builder()
                .threadId(thread.getId())
                .bookingId(thread.getBooking() != null ? thread.getBooking().getId() : null)
                .subject(thread.getSubject())
                .status(thread.getStatus())
                .lastMessageAt(thread.getLastMessageAt())
                .unreadCount(thread.getUnreadCount())
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .threadId(message.getThread() != null ? message.getThread().getId() : null)
                .senderType(message.getSenderType())
                .content(message.getContent())
                .moderationStatus(message.getModerationStatus())
                .moderationNote(message.getModerationNote())
                .createdAt(message.getCreatedAt())
                .moderatedAt(message.getModeratedAt())
                .attachments(message.getAttachments() == null ? Collections.emptyList() : message.getAttachments().stream()
                        .map(att -> MessageAttachmentResponse.builder()
                                .id(att.getId())
                                .fileUrl(att.getFileUrl())
                                .fileName(att.getFileName())
                                .contentType(att.getContentType())
                                .sizeBytes(att.getSizeBytes())
                                .moderationStatus(att.getModerationStatus())
                                .moderationNote(att.getModerationNote())
                                .build())
                        .toList())
                .build();
    }

    private User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
