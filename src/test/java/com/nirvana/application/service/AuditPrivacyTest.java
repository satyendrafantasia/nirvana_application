package com.nirvana.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPrivacyTest {
    @Test void sensitiveKeysAreRemovedRecursivelyAndOperationalStatusIsPreserved() throws Exception {
        var state = new ObjectMapper().readTree("""
            {"status":"CONFIRMED","entries":[{"refresh_token":"private-marker","totp":123456,"phone":"private-marker"}],
             "note":"Contact person@example.test","participants":["private@example.test"]}
            """);
        AuditTrailService.redact(state);
        assertThat(state.toString()).contains("CONFIRMED","[email]").doesNotContain(
                "private-marker", "123456", "person@", "private@", "refresh_token");
    }
}
