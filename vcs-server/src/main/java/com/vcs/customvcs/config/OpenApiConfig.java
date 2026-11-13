package com.vcs.customvcs.config;

import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;

@Configuration
@OpenAPIDefinition(info = @Info(title = "CustomVCS API", version = "1.0",
        description = "Get /api/auth/csrf, paste its token into Authorize, then register or log in. Refresh the token after authentication. The browser sends the session cookie automatically."),
        security = @SecurityRequirement(name = "csrf"))
@SecurityScheme(name = "csrf", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER,
        paramName = "X-CSRF-TOKEN", description = "Token returned by GET /api/auth/csrf; refresh after login/register/logout.")
public class OpenApiConfig {
}
