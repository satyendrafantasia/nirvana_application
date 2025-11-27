package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "UserRegistrationRequest", description = "Payload for registering a new Nirvana user account.")
public class UserRegistrationDTO {

    @NotBlank(message = "Email address cannot be empty")
    @Email(message = "Invalid email address")
    @Schema(description = "Email that acts as username", example = "guest@nirvana.test")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 6, max = 20, message = "Password must be between 6 to 20 characters")
    @Schema(description = "Password meeting platform policy", example = "SpaPass123")
    private String password;

    @NotBlank(message = "Name cannot be empty")
    @Pattern(regexp = "^(?!\\s*$)[A-Za-z ]+$", message = "Name must only contain letters")
    @Schema(description = "First name of the user", example = "Ava")
    private String name;

    @NotBlank(message = "Last name cannot be empty")
    @Pattern(regexp = "^(?!\\s*$)[A-Za-z ]+$", message = "Last name must only contain letters")
    @Schema(description = "Last name of the user", example = "Patel")
    private String lastName;

    @Schema(description = "Role being requested", example = "CUSTOMER")
    private RoleType roleType;

    @Schema(description = "Marketing consent opt-in", example = "true")
    private Boolean marketingOptIn = Boolean.TRUE;

    @Schema(description = "Whether privacy policy is accepted", example = "true")
    private Boolean acceptPrivacyPolicy = Boolean.FALSE;

    @Schema(description = "Version of consent document accepted", example = "v1")
    private String consentVersion = "v1";

    @Schema(description = "Channel through which consent was recorded", example = "registration")
    private String consentSource = "registration";

    @Schema(description = "User timezone", example = "Asia/Kolkata")
    private String timezone = "UTC";

    @Schema(description = "Locale preferred for communication", example = "en")
    private String locale = "en";

}
