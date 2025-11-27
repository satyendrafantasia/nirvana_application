package com.nirvana.application.service;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.enums.RefundRoute;

public interface RefundService {

    void processRefund(Payment payment, Booking booking, RefundRoute route, Integer refundAmountCents);
}
