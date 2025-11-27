package com.nirvana.application.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Nirvana SPA Booking API",
                version = "${springdoc.version:v1}",
                description = "Nirvana platform APIs enabling users to discover spas, purchase packages, book sessions, redeem corporate benefits, and manage experiences.",
                termsOfService = "https://nirvana.test/terms",
                contact = @Contact(name = "Nirvana API Team", email = "api-support@nirvana.test"),
                license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")
        ),
        servers = {
                @Server(description = "Local", url = "http://localhost:8080"),
                @Server(description = "Staging/Prod", url = "https://api.nirvana.test")
        },
        tags = {
                @Tag(name = "Auth", description = "Authentication, registration and session management"),
                @Tag(name = "User", description = "User profile, preferences and favorites"),
                @Tag(name = "Spa", description = "Spa discovery, details and onboarding"),
                @Tag(name = "Service", description = "Individual spa services and availability"),
                @Tag(name = "Booking", description = "Booking creation, reschedule and cancellation"),
                @Tag(name = "Package", description = "Global and spa-specific packages"),
                @Tag(name = "Membership", description = "Memberships and subscriptions"),
                @Tag(name = "Corporate", description = "Corporate onboarding, deals and coupons"),
                @Tag(name = "Payment", description = "Payment initiation, confirmation and refunds"),
                @Tag(name = "Review", description = "Ratings, reviews and feedback"),
                @Tag(name = "Admin", description = "Administrative and operational endpoints")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {

    @Value("${springdoc.version:v1}")
    private String version;
}
