package com.nirvana.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerPushTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final OwnerPush push = new OwnerPush(db,new ObjectMapper());

    @Test void unclaimedSpaCannotEnroll() {
        assertThrows(ResponseStatusException.class, () -> push.subscribe(1,2,"device"));
        verify(db).queryForObject(contains("c.status='APPROVED'"),eq(Boolean.class),eq(1L),eq(2L));
        verifyNoMoreInteractions(db);
    }

    @Test void removedOrTransferredDeviceIsNotSentAnyBooking() {
        push.enabled=true;
        push.project=push.apiKey=push.appId=push.sender=push.vapid="configured";
        when(db.queryForList(contains("m.user_id=c.user_id"),eq(String.class),eq(3L),eq(1L)))
                .thenReturn(List.of());
        assertThrows(BookingContactUnavailable.class, () -> push.send(3,42,1,"CONFIRMED"));
        verify(db).queryForList(contains("m.user_id=c.user_id"),eq(String.class),eq(3L),eq(1L));
        verifyNoMoreInteractions(db);
    }
}
