package com.nirvana.application.controller;

import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.health.HealthFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Guest health and consent forms. Guests read and fill the form for their own bookings; the spa's owner,
 * managers and front-desk staff read it on the appointment; only the owner or a manager sets the questions.
 * Responses carrying answers are marked no-store so browsers and proxies do not keep them.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthFormController {
    private final HealthFormService forms;

    private static <T> ResponseEntity<T> private_(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/me/bookings/{booking}/health-form")
    public ResponseEntity<Map<String, Object>> mine(@PathVariable long booking) {
        return private_(forms.guestView(booking, SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/me/bookings/{booking}/health-form")
    public ResponseEntity<Map<String, Object>> submit(@PathVariable long booking, @RequestBody HealthFormService.Submission input) {
        return private_(forms.submit(booking, SecurityUtils.getCurrentUserId(), input));
    }

    @DeleteMapping("/me/bookings/{booking}/health-form")
    public void withdraw(@PathVariable long booking) {
        forms.withdraw(booking, SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/owner/spas/{spa}/desk/bookings/{booking}/health-form")
    @PreAuthorize("@staffAccess.desk(#spa)")
    public ResponseEntity<Map<String, Object>> forSpa(@PathVariable long spa, @PathVariable long booking) {
        return private_(forms.spaView(spa, booking));
    }

    public record Prompts(List<String> prompts) {}

    @GetMapping("/owner/spas/{spa}/services/{service}/health-questions")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public List<HealthFormService.Question> questions(@PathVariable long spa, @PathVariable long service) {
        return forms.questions(spa, service);
    }

    @PutMapping("/owner/spas/{spa}/services/{service}/health-questions")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public List<HealthFormService.Question> questions(@PathVariable long spa, @PathVariable long service, @RequestBody Prompts input) {
        return forms.replaceQuestions(spa, service, input.prompts());
    }

    public record Terms(String terms) {}

    @GetMapping("/owner/spas/{spa}/health-terms")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public Terms terms(@PathVariable long spa) {
        return new Terms(forms.terms(spa));
    }

    @PutMapping("/owner/spas/{spa}/health-terms")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public Terms terms(@PathVariable long spa, @RequestBody Terms input) {
        return new Terms(forms.setTerms(spa, input.terms()));
    }
}
