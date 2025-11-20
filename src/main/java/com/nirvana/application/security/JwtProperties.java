
package com.nirvana.application.security;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /**
     * Secret must be sufficiently long (recommend 256+ bits). Use env/secret manager in production.
     * Example (application.properties):
     * jwt.secret=base64EncodedSecretHere
     * jwt.expiration-ms=3600000
     */
    private String secret;
    private long expirationMs = 3600000L;
    private String issuer = "nirvana-app";
    private String header = "Authorization";
    private String tokenPrefix = "Bearer ";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getExpirationMs() { return expirationMs; }
    public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }
    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }
}
