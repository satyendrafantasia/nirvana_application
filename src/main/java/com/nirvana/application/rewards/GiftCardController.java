package com.nirvana.application.rewards;

import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Buying gift cards and checking a code's balance. */
@RestController
@RequestMapping("/api/v1/gift-cards")
@RequiredArgsConstructor
public class GiftCardController {

    private final GiftCardService giftCards;

    public record Purchase(@NotNull @Positive Integer amountCents, Long spaId, @Size(max = 100) String recipientName,
                           @NotBlank @Email @Size(max = 255) String recipientEmail, @Size(max = 300) String message) {
    }

    @PostMapping("/checkout")
    public GiftCardService.Checkout start(@Valid @RequestBody Purchase input) {
        return giftCards.startPurchase(SecurityUtils.getCurrentUserId(),
                new GiftCardService.PurchaseInput(input.amountCents(), input.spaId(), input.recipientName(), input.recipientEmail(), input.message()));
    }

    /** Picks up a payment that was captured while the browser was away. */
    @PostMapping("/{id}/resume")
    public GiftCardService.Card resume(@PathVariable long id) {
        return giftCards.resume(SecurityUtils.getCurrentUserId(), id);
    }

    @PostMapping("/{id}/confirm")
    public GiftCardService.Card confirm(@PathVariable long id, @Valid @RequestBody RazorpayConfirmRequest request) {
        return giftCards.confirm(SecurityUtils.getCurrentUserId(), id, request);
    }

    /** Gift cards the customer bought, with their codes, so they can pass one on themselves. */
    @GetMapping("/mine")
    public List<GiftCardService.Card> mine() {
        return giftCards.purchasedBy(SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/balance")
    public GiftCardService.Balance balance(@RequestParam @Size(max = 32) String code) {
        return giftCards.balance(code);
    }
}
