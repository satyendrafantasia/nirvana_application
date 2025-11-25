package com.nirvana.application.service;

import com.nirvana.application.model.dto.*;

public interface RefundManagementService {

    RefundResponseDto requestRefund(Long bookingId, Long userId, RefundRequestDto request);

    RefundResponseDto approveOrProcessRefund(Long bookingId, Long adminUserId, AdminRefundDecisionRequest request);

    RefundResponseDto latestRefundForBooking(Long bookingId, Long userId);

    VoucherResponse issueVoucher(Long bookingId, Long adminUserId, VoucherIssueRequest request);
}
