package com.nirvana.application.service;

import com.nirvana.application.model.dto.SlotHoldRequest;
import com.nirvana.application.model.dto.SlotHoldResponse;

public interface BookingHoldService {

    SlotHoldResponse holdSlot(Long userId, SlotHoldRequest request);
}
