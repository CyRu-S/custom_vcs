package com.vcs.customvcs.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import com.vcs.customvcs.dto.*;
import com.vcs.customvcs.security.*;
import com.vcs.customvcs.service.AuthService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final CurrentUser current;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;

    public AuthController(AuthService auth, CurrentUser current,
            HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf) {
        this.auth = auth; this.current = current; this.contexts = contexts; this.csrf = csrf;
    }

    // The SPA keeps this token in memory and sends it with POST/PATCH/PUT/DELETE requests.
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionUser register(@Valid @RequestBody RegisterRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return signIn(auth.register(body), request, response);
    }

    @PostMapping("/login")
    public SessionUser login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return signIn(auth.login(body), request, response);
    }

    @GetMapping("/me")
    public SessionUser me() { return auth.profile(current.get().id()); }

    @PatchMapping("/me")
    public SessionUser profile(@Valid @RequestBody ProfileRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        var user = auth.rename(current.get().id(), body.name());
        saveSession(user, request, response);
        return user;
    }

    private SessionUser signIn(SessionUser user, HttpServletRequest request, HttpServletResponse response) {
        var authentication = authentication(user);
        // Rotate an existing session ID and CSRF token at the authentication boundary.
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
        new CsrfAuthenticationStrategy(csrf).onAuthentication(authentication, request, response);
        saveSession(user, request, response);
        return user;
    }

    private UsernamePasswordAuthenticationToken authentication(SessionUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private void saveSession(SessionUser user, HttpServletRequest request, HttpServletResponse response) {
        // A JSON login endpoint must explicitly persist authentication for later HTTP requests.
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication(user));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }
}
