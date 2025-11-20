// java
// `src/main/java/com/nirvana/application/dto/LoginResponse.java`
package com.nirvana.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String tokenType;
    private long expiresAt;
    private String username;
}
