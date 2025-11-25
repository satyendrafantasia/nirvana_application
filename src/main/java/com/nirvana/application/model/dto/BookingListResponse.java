package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingListResponse {
    private PagedResponse<BookingSummaryResponse> page;
}
