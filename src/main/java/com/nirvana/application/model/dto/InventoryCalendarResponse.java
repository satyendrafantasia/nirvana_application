package com.nirvana.application.model.dto;

import java.util.List;

public class InventoryCalendarResponse {
    private List<InventoryDayResponse> days;

    public InventoryCalendarResponse() {
    }

    public InventoryCalendarResponse(List<InventoryDayResponse> days) {
        this.days = days;
    }

    public List<InventoryDayResponse> getDays() {
        return days;
    }

    public void setDays(List<InventoryDayResponse> days) {
        this.days = days;
    }
}
