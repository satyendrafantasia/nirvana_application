package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleBookingConfigResponse {
    private GoogleBookingSpaSummary spa;
    private List<ServiceSummaryResponse> services;
    private Boolean therapistSelectionEnabled;
    private Boolean therapistTypeSelectionEnabled;
}
