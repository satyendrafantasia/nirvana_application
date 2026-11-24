package com.nirvana.application.controller;

import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.OwnerPush;
import com.nirvana.application.service.SpaBookingReplies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/push")
@PreAuthorize("@staffAccess.staff()")
public class OwnerPushController {
    private final OwnerPush push;
    private final SpaBookingReplies replies;
    public record Device(@NotBlank @Size(max=2048) String token) {}

    @GetMapping("/spas/{spa}")
    public Map<String,Object> config(@PathVariable long spa) {
        return push.config(spa, SecurityUtils.getCurrentUserId());
    }
    @PutMapping("/spas/{spa}")
    public void subscribe(@PathVariable long spa,@Valid @RequestBody Device device) {
        push.subscribe(spa,SecurityUtils.getCurrentUserId(),device.token());
    }
    @PostMapping("/unsubscribe")
    public void unsubscribe(@Valid @RequestBody Device device) {
        push.unsubscribe(SecurityUtils.getCurrentUserId(),device.token());
    }
    @GetMapping("/bookings/{id}")
    public SpaBookingReplies.View view(@PathVariable long id) {
        return replies.ownerReview(id,SecurityUtils.getCurrentUserId(),null,null);
    }
    @PostMapping("/bookings/{id}/{action:confirm|decline}")
    public SpaBookingReplies.View answer(@PathVariable long id,@PathVariable String action,
            @Valid @RequestBody(required=false) SpaBookingReplyController.DeclineRequest input) {
        return replies.ownerReview(id,SecurityUtils.getCurrentUserId(),action,input==null?null:input.reason());
    }
}
