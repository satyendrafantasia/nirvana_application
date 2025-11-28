package com.nirvana.application.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisteredUserDto {
    private Long id;
    private String phoneNumber;
    private String email;
    private String fullName;
}
