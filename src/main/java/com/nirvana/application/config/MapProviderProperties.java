package com.nirvana.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "maps")
public class MapProviderProperties {

    private String provider = "google";
    private ProviderDetails google = new ProviderDetails();
    private ProviderDetails mapbox = new ProviderDetails();

    public String getActiveApiKey() {
        return switch (provider.toLowerCase()) {
            case "mapbox" -> mapbox.getApiKey();
            default -> google.getApiKey();
        };
    }

    public String getActiveBaseUrl() {
        return switch (provider.toLowerCase()) {
            case "mapbox" -> mapbox.getBaseUrl();
            default -> google.getBaseUrl();
        };
    }

    public String getActiveStaticStyle() {
        return switch (provider.toLowerCase()) {
            case "mapbox" -> mapbox.getStaticStyle();
            default -> google.getStaticStyle();
        };
    }

    @Getter
    @Setter
    public static class ProviderDetails {
        private String apiKey;
        private String baseUrl;
        private String staticStyle;
    }
}
