package com.nirvana.application.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.config.SentryScrubbing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeoMarkupTest {
    @Test void structuredDataCannotCloseItsScriptTagAndStillParses() throws Exception {
        String name = "</script><script>alert(1)</script> & \"Spa\"  ";
        String out = SeoMarkup.jsonLd(Map.of("name", name));
        assertThat(out).doesNotContain("<", ">", "&", " ");
        assertThat(new ObjectMapper().readTree(out).path("name").asText()).isEqualTo(name);
    }

    @Test void headEscapesAttributesAndText() {
        var page = new SeoMarkup.Page("A \"quoted\" <b>name</b>", "x' onload='alert(1)", "/spas/1", true, null, "website", List.of(), "");
        String head = SeoMarkup.head(page, "https://nirvana.example.com");
        assertThat(head).contains("<title data-server-head>A &quot;quoted&quot; &lt;b&gt;name&lt;/b&gt;</title>");
        assertThat(head).contains("content=\"x&#39; onload=&#39;alert(1)\"").doesNotContain("<b>");
        assertThat(head).contains("content=\"https://nirvana.example.com/nirvana-social.png\"");
    }

    @Test void previewDeploymentsAreNeverIndexed() {
        var page = new SeoMarkup.Page("Spa", "d", "/spas/1", true, null, "website", List.of(), "");
        assertThat(SeoMarkup.head(page, "")).contains("noindex,follow").doesNotContain("canonical", "og:url");
        SeoPages preview = new SeoPages(null, null, new PublicOrigin(""));
        assertThat(preview.robots()).isEqualTo("User-agent: *\nDisallow: /\n");
        assertThat(preview.sitemap()).doesNotContain("<loc>");
    }

    @Test void onlyAnApprovedHttpsOriginIsAccepted() {
        assertThat(PublicOrigin.parse("https://Nirvana.example.com/")).isEqualTo("https://nirvana.example.com");
        for (String bad : List.of("http://nirvana.example.com", "https://localhost", "https://preview.test",
                "https://10.0.0.1", "https://nirvana.example.com/app", "https://u:p@nirvana.example.com", "https://x.com/?a=1"))
            assertThatThrownBy(() -> PublicOrigin.parse(bad)).as(bad).isInstanceOf(IllegalStateException.class);
    }

    @Test void summariesEndOnAWord() {
        assertThat(SeoMarkup.summary("one two three four", 12)).isEqualTo("one two…");
        assertThat(SeoMarkup.summary("  short\n text ", 50)).isEqualTo("short text");
    }

    @Test void errorReportsCarryNoPersonalData() {
        String out = SentryScrubbing.scrub("Login failed for priya@example.com +91 98765 43210 otp=4821 code 482913 Bearer abc.def token=xyz");
        assertThat(out).doesNotContain("priya", "98765", "4821", "482913", "abc.def", "xyz");
        assertThat(SentryScrubbing.scrubUrl("https://api.nirvana.in/api/auth/otp?phone=9876543210#x")).isEqualTo("https://api.nirvana.in/api/auth/otp");
    }
}
