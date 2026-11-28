package com.nirvana.application.service.recovery;

import com.nirvana.application.model.Booking;
import com.nirvana.application.service.GuestWhatsApp;
import com.nirvana.application.service.SpaAlertWhatsApp;
import com.nirvana.application.service.impl.BookingEmailContent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The checkout reminder on WhatsApp. Off by default: it needs its own approved Marketing template
 * in WhatsApp Manager (see docs/checkout-recovery.md) and the guest WhatsApp number. Recipients
 * are the same as for guest booking updates ({@link GuestWhatsApp#recipient}): guests who agreed
 * to WhatsApp messages and verified their mobile.
 *
 * <p>Template {@code nirvana_checkout_reminder}, body:
 * "Hi {{1}}, you were booking {{2}} at {{3}} for {{4}} but the payment didn't go through, so
 * nothing is booked yet. Tap below to pick up where you left off." with a URL button
 * {@code https://<frontend>/booking/resume/{{1}}} completed by the signed token.
 */
@Component
public class CheckoutRecoveryWhatsApp {
    private final GuestWhatsApp guests;
    private final SpaAlertWhatsApp whatsApp;
    private final boolean enabled;
    private final String template;

    public CheckoutRecoveryWhatsApp(GuestWhatsApp guests, SpaAlertWhatsApp whatsApp,
                                    @Value("${checkout-recovery.whatsapp.enabled:false}") boolean enabled,
                                    @Value("${checkout-recovery.whatsapp.template:nirvana_checkout_reminder}") String template) {
        this.guests = guests;
        this.whatsApp = whatsApp;
        this.enabled = enabled;
        this.template = template;
    }

    /** The guest's WhatsApp number when this channel is switched on and the guest can receive it. */
    public Optional<String> recipient(Booking b) {
        return enabled ? guests.recipient(b) : Optional.empty();
    }

    /** Throws when WhatsApp refuses the message. */
    public void send(String to, Booking b, String resumeToken) {
        whatsApp.sendTemplate(to, template, values(b), resumeToken, "guest checkout reminder");
    }

    static List<String> values(Booking b) {
        String name = b.getUser() != null && b.getUser().getName() != null && !b.getUser().getName().isBlank()
                ? b.getUser().getName().strip() : "there";
        String treatment = b.getService() != null ? b.getService().getName() : "your treatment";
        String spa = b.getSpa() != null ? b.getSpa().getName() : "the spa";
        return List.of(name, treatment, spa, BookingEmailContent.formatStart(b.getStartTs(), b.getSpa()));
    }
}
