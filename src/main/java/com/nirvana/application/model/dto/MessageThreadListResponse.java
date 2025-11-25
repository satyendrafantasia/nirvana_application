package com.nirvana.application.model.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MessageThreadListResponse {
    List<MessageThreadResponse> threads;
    long totalElements;
    int totalPages;
    int page;
    int size;
}
