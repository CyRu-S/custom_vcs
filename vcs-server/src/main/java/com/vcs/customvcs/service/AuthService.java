package com.vcs.customvcs.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.vcs.customvcs.dto.*;
import com.vcs.customvcs.entity.UserEntity;
import com.vcs.customvcs.repository.UserRepository;
import com.vcs.customvcs.security.SessionUser;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    // Persist only a BCrypt hash; the public session identity never contains credentials.
    public SessionUser register(RegisterRequest request) {
        requirePasswordLength(request.password());
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw duplicateEmail();
        var entity = UserEntity.builder().id(UUID.randomUUID()).name(request.name().strip())
                .email(email).passwordHash(passwords.encode(request.password())).createdAt(Instant.now()).build();
        try {
            return publicUser(users.saveAndFlush(entity));
        } catch (DataIntegrityViolationException failure) {
            // The database also protects against two simultaneous registrations for one email.
            throw duplicateEmail();
        }
    }

    public SessionUser login(LoginRequest request) {
        requirePasswordLength(request.password());
        var entity = users.findByEmail(request.email().strip().toLowerCase(Locale.ROOT)).orElse(null);
        // Do a hash comparison even for unknown emails; both failures share one public message.
        boolean matched = passwords.matches(request.password(), entity == null ? dummyHash : entity.getPasswordHash());
        if (entity == null || !matched) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        return publicUser(entity);
    }

    // Read profile changes from SQL instead of treating the session's display name as permanent.
    public SessionUser profile(UUID id) {
        return publicUser(users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again.")));
    }

    public SessionUser rename(UUID id, String name) {
        var entity = users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again."));
        entity.setName(name.strip());
        return publicUser(users.saveAndFlush(entity));
    }

    private void requirePasswordLength(String password) {
        // BCrypt limits bytes, so a character-count annotation alone is insufficient for Unicode passwords.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes.");
        }
    }

    private SessionUser publicUser(UserEntity entity) {
        return new SessionUser(entity.getId(), entity.getName(), entity.getEmail());
    }
    private ResponseStatusException duplicateEmail() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
    }
}
