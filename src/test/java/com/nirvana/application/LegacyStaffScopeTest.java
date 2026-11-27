package com.nirvana.application;

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.BlackoutWindowRequest;
import com.nirvana.application.model.dto.SupportOverrideRequest;
import com.nirvana.application.repository.*;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.BlackoutService;
import com.nirvana.application.service.ProviderAuditService;
import com.nirvana.application.service.SupportOverrideService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyStaffScopeTest {
    @BeforeEach void staffSession() {
        var actor = new UserPrincipal(11L,"fixture","fixture@example.test","",true,List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities()));
    }
    @AfterEach void clean() { SecurityContextHolder.clearContext(); }

    @Test void blackoutNeedsALocalProviderAssignmentAndRecordsTheSessionActor() {
        var windows = mock(BlackoutWindowRepository.class);
        var spas = mock(SpaRepository.class);
        var providers = mock(ProviderUserRepository.class);
        var audit = mock(ProviderAuditService.class);
        var service = new BlackoutService(windows,spas,providers,audit);
        var spa = new Spa(); spa.setId(7L);
        when(spas.findById(7L)).thenReturn(Optional.of(spa));
        var input = new BlackoutWindowRequest();
        input.setSpaId(7L); input.setProviderId(15L); input.setCreatedBy("input label");
        input.setStartTs(OffsetDateTime.parse("2027-01-01T10:00:00Z"));
        input.setEndTs(input.getStartTs().plusHours(2));
        assertThrows(IllegalArgumentException.class, () -> service.createBlackout(input));
        verifyNoInteractions(windows, audit);

        var provider = new ProviderUser(); provider.setId(15L);
        when(providers.findAssignedDuring(15L,7L,input.getStartTs(),input.getEndTs())).thenReturn(Optional.of(provider));
        when(windows.save(any(BlackoutWindow.class))).thenAnswer(c -> c.getArgument(0));
        assertEquals("11",service.createBlackout(input).getCreatedBy());
        verify(audit).log(7L,"USER",11L,"BLACKOUT_CREATED",null);
    }

    @Test void adminOverrideRelatesOnlyMatchingSpaAndBookingAndAuditsAuthenticatedActor() {
        var overrides = mock(SupportOverrideRepository.class);
        var bookings = mock(BookingRepository.class);
        var audit = mock(ProviderAuditService.class);
        var service = new SupportOverrideService(overrides,bookings,audit);
        var spa = new Spa(); spa.setId(7L);
        var booking = new Booking(); booking.setId(21L); booking.setSpa(spa);
        when(bookings.findById(21L)).thenReturn(Optional.of(booking));
        var input = new SupportOverrideRequest();
        input.setBookingId(21L); input.setSpaId(8L); input.setOverrideType("desk"); input.setRequestedBy("input label");
        assertThrows(IllegalArgumentException.class, () -> service.requestOverride(input));
        verifyNoInteractions(overrides, audit);
        input.setSpaId(null);
        when(overrides.save(any(SupportOverride.class))).thenAnswer(c -> c.getArgument(0));
        var response = service.requestOverride(input);
        assertEquals(7L,response.getSpaId()); assertEquals("11",response.getRequestedBy());
        verify(audit).log(7L,"SUPPORT",11L,"OVERRIDE_REQUESTED","desk");
    }
}
