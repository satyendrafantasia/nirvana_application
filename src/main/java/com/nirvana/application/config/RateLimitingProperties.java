package com.nirvana.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "reliability.rate-limiting")
public class RateLimitingProperties {

    /** Enable/disable the filter globally */
    private boolean enabled = true;
    /** Number of requests permitted per window */
    private int requests = 100;
    /** Number of requests permitted per authenticated user per window */
    private Integer perUserRequests = 60;
    /** Window size in seconds */
    private int windowSeconds = 60;
    /** Requests permitted per client address per window on sign-in, OTP, registration and password-reset paths */
    private int authRequests = 20;
    /** Signed-out nearby-spa searches permitted per client address per window; each one is a paid Google call */
    private int publicDiscoveryRequests = 5;
    /** Paths that should never be rate limited (health, actuator, etc.). */
    private List<String> whitelistPaths = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRequests() {
        return requests;
    }

    public void setRequests(int requests) {
        this.requests = requests;
    }

    public Integer getPerUserRequests() {
        return perUserRequests;
    }

    public void setPerUserRequests(Integer perUserRequests) {
        this.perUserRequests = perUserRequests;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getAuthRequests() {
        return authRequests;
    }

    public void setAuthRequests(int authRequests) {
        this.authRequests = authRequests;
    }

    public int getPublicDiscoveryRequests() {
        return publicDiscoveryRequests;
    }

    public void setPublicDiscoveryRequests(int publicDiscoveryRequests) {
        this.publicDiscoveryRequests = publicDiscoveryRequests;
    }

    public List<String> getWhitelistPaths() {
        return whitelistPaths;
    }

    public void setWhitelistPaths(List<String> whitelistPaths) {
        this.whitelistPaths = whitelistPaths;
    }
}
