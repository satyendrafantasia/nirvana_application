package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class RevenueBreakdown {
    String period;
    long revenueCents;
}
