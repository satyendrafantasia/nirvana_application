package com.nirvana.application.service;

import com.nirvana.application.model.Slot;
import com.nirvana.application.model.dto.WaitlistRequest;
import com.nirvana.application.model.dto.WaitlistResponse;

public interface WaitlistService {

    WaitlistResponse joinWaitlist(Long userId, WaitlistRequest request);

    void tryNotifySlotAvailable(Slot slot);
}
