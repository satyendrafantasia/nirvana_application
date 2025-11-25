package com.nirvana.application.controller;

import com.nirvana.application.model.dto.*;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.InvoiceService;
import com.nirvana.application.service.RefundManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings/{bookingId}")
@RequiredArgsConstructor
public class RefundController {

    private final RefundManagementService refundManagementService;
    private final InvoiceService invoiceService;

    @PostMapping("/refunds")
    public ResponseEntity<RefundResponseDto> requestRefund(@PathVariable Long bookingId,
                                                           @Valid @RequestBody RefundRequestDto request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(refundManagementService.requestRefund(bookingId, userId, request));
    }

    @GetMapping("/refunds/latest")
    public ResponseEntity<RefundResponseDto> latestRefund(@PathVariable Long bookingId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(refundManagementService.latestRefundForBooking(bookingId, userId));
    }

    @PostMapping("/refunds/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RefundResponseDto> approveRefund(@PathVariable Long bookingId,
                                                           @Valid @RequestBody AdminRefundDecisionRequest request) {
        Long adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(refundManagementService.approveOrProcessRefund(bookingId, adminId, request));
    }

    @PostMapping("/vouchers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VoucherResponse> issueVoucher(@PathVariable Long bookingId,
                                                        @Valid @RequestBody VoucherIssueRequest request) {
        Long adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(refundManagementService.issueVoucher(bookingId, adminId, request));
    }

    @PostMapping("/invoice/pdf")
    public ResponseEntity<InvoiceResponseDto> ensureInvoicePdf(@PathVariable Long bookingId) {
        return ResponseEntity.ok(invoiceService.generateInvoiceForBooking(bookingId));
    }
}
